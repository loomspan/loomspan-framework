package ai.loomspan.internal.skill;

import ai.loomspan.api.*;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.core.*;
import ai.loomspan.internal.runtime.input.SkillInputContractResolver;
import ai.loomspan.internal.autoconfigure.NamedAiConnectionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BiFunction;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkillReloaderTest {
    static final ExecutionConfiguration EMPTY = new ExecutionConfiguration("loomspan: {}");
    static final ExecutionConfiguration MODEL = new ExecutionConfiguration("""
            loomspan:
              connections: {local: {driver: ollama, base-url: 'http://localhost:11434'}}
              models: {model: {connection: local, provider-model: fixture}}
            """);

    @Test void targetOnlyReplacementResetsVisibilityConfigurationAndOmittedSkills() {
        var fixture = new Fixture(null);
        var a = fixture.reloader.prepare("A", List.of(model("old"), model("keep")), new ExecutionConfiguration(MODEL.yaml()+"  session: {max-depth: 100}\n"), NamespaceVisibility.PUBLIC);
        fixture.reloader.publish(a);
        var b = fixture.reloader.prepare("b", List.of(), EMPTY); fixture.reloader.publish(b);
        var retained = fixture.reloader.snapshot("a");
        var replacement = fixture.reloader.prepare("a", List.of(model("keep")), MODEL); fixture.reloader.publish(replacement);
        assertThat(fixture.manager.active("a").visibility()).isEqualTo(NamespaceVisibility.PRIVATE);
        assertThat(fixture.manager.active("a").runtime().properties().getSession().getMaxDepth()).isEqualTo(32);
        assertThat(fixture.startup.getSession().getMaxDepth()).isEqualTo(91);
        assertThat(fixture.reloader.snapshot("b").generationId()).isEqualTo(b.generationId());
        assertThat(fixture.reloader.snapshot("a").skill("a-old")).isEmpty();
        assertThat(retained.skill("a-old")).isPresent();
        assertThatThrownBy(() -> fixture.reloader.prepare("a", List.of(model("keep")), EMPTY)).isInstanceOf(SkillReloadException.class);
        var empty = fixture.reloader.prepare("a", List.of(), EMPTY); fixture.reloader.publish(empty);
        assertThat(fixture.reloader.snapshot("a").namespace()).isEqualTo("a");
        assertThat(fixture.reloader.snapshot("a").skills()).isEmpty();
    }

    @Test void capturedBaseIsNotThePreviouslyReadRevisionAndAbsenceIsNotHistory() {
        var f = new Fixture(null);
        String first = f.reloader.snapshot("default").generationId();
        var second = f.reloader.prepare("default", List.of(), EMPTY); f.reloader.publish(second);
        var third = f.reloader.prepare("default", List.of(), EMPTY); f.reloader.publish(third);
        assertThat(third.generationId()).isNotEqualTo(first).isNotEqualTo(second.generationId());
        var stale = f.reloader.prepare("default", List.of(), EMPTY);
        f.reloader.remove("DEFAULT", third.generationId());
        assertThatThrownBy(() -> f.reloader.publish(stale)).hasMessageContaining("stale");
        var absent = f.reloader.prepare("default", List.of(), EMPTY);
        var incarnation = f.reloader.prepare("default", List.of(), EMPTY); f.reloader.publish(incarnation);
        f.reloader.remove("default", incarnation.generationId());
        f.reloader.publish(absent);
        assertThat(f.reloader.snapshot("default").generationId()).isEqualTo(absent.generationId());
        assertThat(List.of(first, second.generationId(), third.generationId(), stale.generationId(), absent.generationId(), incarnation.generationId())).doesNotHaveDuplicates();
        assertThatThrownBy(() -> f.reloader.remove("default", first)).isInstanceOf(SkillReloadException.class);
        f.reloader.remove("default", absent.generationId());
        f.manager.afterSingletonsInstantiated();
        assertThatThrownBy(() -> f.reloader.snapshot("default")).isInstanceOf(SkillReloadException.class);
        assertThatThrownBy(() -> f.manager.capture(SkillIdentity.parse("default-root"))).isInstanceOf(SkillReloadException.class);
        assertThatThrownBy(() -> f.reloader.remove("default", absent.generationId())).isInstanceOf(SkillReloadException.class);
    }

    @Test void dependencyChangesNeverInvalidateTargetPublication() {
        for (String change : List.of("replace", "private", "remove", "recreate")) {
            var f = new Fixture(null);
            var dep = f.reloader.prepare("dep", List.of(model("leaf")), MODEL, NamespaceVisibility.PUBLIC); f.reloader.publish(dep);
            var caller = f.reloader.prepare("caller", List.of(calls("root", "dep-leaf")), MODEL);
            if (change.equals("remove") || change.equals("recreate")) f.reloader.remove("dep", dep.generationId());
            if (!change.equals("remove")) f.reloader.publish(f.reloader.prepare("dep", List.of(), EMPTY));
            f.reloader.publish(caller);
            assertThat(f.reloader.snapshot("caller").generationId()).isEqualTo(caller.generationId());
        }
    }

    @Test void everyManagementBoundaryNormalizesAndChecksNamespaceLength() {
        var f = new Fixture(null); String max = "n".repeat(32), tooLong = max+"n";
        assertThat(f.reloader.validate(max.toUpperCase(), List.of(), EMPTY).valid()).isTrue();
        var candidate = f.reloader.prepare(max.toUpperCase(), List.of(), EMPTY); f.reloader.publish(candidate);
        assertThat(f.reloader.snapshot(max.toUpperCase()).namespace()).isEqualTo(max);
        f.reloader.remove(max.toUpperCase(), candidate.generationId());
        assertThatThrownBy(() -> f.reloader.validate(tooLong, List.of(), EMPTY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> f.reloader.prepare(tooLong, List.of(), EMPTY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> f.reloader.snapshot(tooLong)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> f.reloader.remove(tooLong, "id")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> f.reloader.prepare("default", List.of(), null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> f.reloader.prepare("default", List.of(), EMPTY, (NamespaceVisibility)null)).isInstanceOf(NullPointerException.class);
        for (String key : List.of("shutdown", "skills", "observability", "unknown"))
            assertThat(f.reloader.validate("default", List.of(), new ExecutionConfiguration("loomspan: {"+key+": {}} ")).valid()).isFalse();
    }

    @Test void preparationsProgressConcurrentlyForSameAndDifferentTargets() throws Exception {
        for (String secondTarget : List.of("default", "other")) {
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var builds = new AtomicInteger();
            var f = new Fixture((properties, policy) -> {
                if (builds.incrementAndGet()==3) { entered.countDown(); await(release); }
                return runtime(properties, policy);
            });
            var ready = f.reloader.prepare("unrelated", List.of(), EMPTY);
            try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
                var blocked = workers.submit(() -> f.reloader.prepare("default", List.of(model("slow")), MODEL));
                try {
                    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                    var peer = workers.submit(() -> f.reloader.prepare(secondTarget, List.of(model("fast")), MODEL)).get(5, TimeUnit.SECONDS);
                    assertThat(workers.submit(() -> f.reloader.snapshot("default")).get(5, TimeUnit.SECONDS).namespace()).isEqualTo("default");
                    workers.submit(() -> f.reloader.publish(ready)).get(5, TimeUnit.SECONDS);
                    assertThat(peer.snapshot().skill(secondTarget+"-fast")).isPresent();
                    release.countDown();
                    var slow = blocked.get(5, TimeUnit.SECONDS);
                    assertThat(slow.snapshot().skill("default-slow")).isPresent();
                    assertThat(slow.snapshot().skill("default-fast")).isEmpty();
                    assertThat(slow.generationId()).isNotEqualTo(peer.generationId());
                    slow.close(); peer.close();
                } finally { release.countDown(); }
            }
        }
    }

    @Test void competingCreationAndReplacementHaveExactlyOneWinner() throws Exception {
        for (String target : List.of("new", "default")) {
            var f = new Fixture(null); var first = f.reloader.prepare(target, List.of(), EMPTY); var second = f.reloader.prepare(target, List.of(), EMPTY);
            var start = new CountDownLatch(1);
            try (var workers=Executors.newVirtualThreadPerTaskExecutor()) {
                var a=workers.submit(() -> publishAfter(start,f.reloader,first)); var b=workers.submit(() -> publishAfter(start,f.reloader,second));
                start.countDown(); assertThat(List.of(a.get(5,TimeUnit.SECONDS),b.get(5,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
                assertThat(f.reloader.snapshot(target).generationId()).isIn(first.generationId(),second.generationId());
            }
        }
    }

    @Test void candidatesRejectForeignClosedPublishedAndShutdownWithoutClosingOtherOwners() throws Exception {
        var resources = new CopyOnWriteArrayList<NamedAiConnectionRegistry>();
        var f = new Fixture((p, policy) -> { var registry=mock(NamedAiConnectionRegistry.class); resources.add(registry); return new ExecutionRuntime(p,policy,registry); });
        var foreign = new DefaultSkillReloader(f.manager, f.lifecycle);
        var abandoned=f.reloader.prepare("default",List.of(),EMPTY);
        assertThatThrownBy(() -> foreign.publish(abandoned)).isInstanceOf(SkillReloadException.class);
        verify(resources.get(1),never()).destroy();
        abandoned.close(); abandoned.close(); verify(resources.get(1),times(1)).destroy();
        assertThatThrownBy(() -> f.reloader.publish(abandoned)).hasMessageContaining("closed");
        var winner=f.reloader.prepare("default",List.of(),EMPTY); var loser=f.reloader.prepare("default",List.of(),EMPTY);
        f.reloader.publish(winner); winner.close();
        assertThatThrownBy(() -> f.reloader.publish(winner)).hasMessageContaining("already published");
        assertThatThrownBy(() -> f.reloader.publish(loser)).hasMessageContaining("stale");
        verify(resources.get(2),never()).destroy(); verify(resources.get(3),times(1)).destroy();
        var shutdown=f.reloader.prepare("default",List.of(),EMPTY); f.lifecycle.closeAdmission();
        assertThatThrownBy(() -> f.reloader.publish(shutdown)).hasMessageContaining("shutdown");
        verify(resources.get(4),times(1)).destroy(); verify(resources.get(2),never()).destroy();
        assertThatThrownBy(() -> f.reloader.prepare("default",List.of(),EMPTY)).hasMessageContaining("shutdown");
    }

    @Test void shutdownDuringBlockedConstructionDisposesCompletedCandidateOnce() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var count=new AtomicInteger();
        var resources=new CopyOnWriteArrayList<NamedAiConnectionRegistry>();
        var f=new Fixture((p,policy)->{var registry=mock(NamedAiConnectionRegistry.class);resources.add(registry);
            if(count.incrementAndGet()==2){entered.countDown();await(release);}return new ExecutionRuntime(p,policy,registry);});
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()){
            var pending=workers.submit(()->f.reloader.prepare("default",List.of(),EMPTY));
            try {assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();f.lifecycle.closeAdmission();release.countDown();
                assertThatThrownBy(()->pending.get(5,TimeUnit.SECONDS)).hasCauseInstanceOf(SkillReloadException.class);
                verify(resources.get(1),times(1)).destroy();verify(resources.get(0),never()).destroy();
            }finally{release.countDown();}
        }
    }

    @Test void removalRetainsOwnersAndCallbacksRunOutsideRegistryMonitor() throws Exception {
        var f=new Fixture(null); f.reloader.publish(f.reloader.prepare("default", List.of(model("root")), MODEL)); var owner=f.manager.capture(SkillIdentity.parse("default-root"));String old=owner.snapshot().entryGeneration().id();
        var delivered=new CopyOnWriteArrayList<String>();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        f.reloader.onGenerationRetired(id->{entered.countDown();await(release);delivered.add(id);});
        f.reloader.onGenerationRetired(id->{throw new IllegalStateException("isolated listener");});
        f.reloader.remove("default",old);assertThat(delivered).isEmpty();
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()){
            var completion=workers.submit(owner.lease()::close);
            try {assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
                workers.submit(()->{var c=f.reloader.prepare("other",List.of(),EMPTY);f.reloader.publish(c);return f.reloader.snapshot("other");}).get(5,TimeUnit.SECONDS);
                release.countDown();completion.get(5,TimeUnit.SECONDS);assertThat(delivered).containsExactly(old);
            }finally{release.countDown();}
        }
        owner.lease().close();assertThat(delivered).containsExactly(old);
    }

    @Test void concurrentCredentialsAndInvalidPeerRemainIsolated() throws Exception {
        var f=new Fixture(null);String config="""
                loomspan:
                  connections: {api: {driver: openai, api-key-ref: key}}
                  models: {model: {connection: api, provider-model: fixture}}
                """;
        var start=new CountDownLatch(1);
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()){
            var a=workers.submit(()->{await(start);return f.reloader.prepare("a",List.of(model("one")),new ExecutionConfiguration(config),Map.of("key","first"));});
            var b=workers.submit(()->{await(start);return f.reloader.prepare("b",List.of(model("two")),new ExecutionConfiguration(config),Map.of("key","second"));});
            var invalid=workers.submit(()->{await(start);return f.reloader.prepare("bad",List.of(new SkillDocument("invalid peer","invalid: [")),MODEL);});
            start.countDown();var first=a.get(5,TimeUnit.SECONDS);var second=b.get(5,TimeUnit.SECONDS);
            assertThatThrownBy(()->invalid.get(5,TimeUnit.SECONDS)).hasCauseInstanceOf(SkillReloadException.class);
            f.reloader.publish(first);f.reloader.publish(second);
            assertThat(f.manager.active("a").runtime().properties().getConnections().get("api").getApiKey()).isEqualTo("first");
            assertThat(f.manager.active("b").runtime().properties().getConnections().get("api").getApiKey()).isEqualTo("second");
            assertThat(first.snapshot().skill("a-one")).isPresent();assertThat(first.snapshot().skill("a-two")).isEmpty();
        }
    }

    @Test void concurrentExecutorPreparationReusesCompletedFixedDiscovery() throws Exception {
        var processor=mock(SkillMethodBeanPostProcessor.class);when(processor.capabilities()).thenReturn(List.of());
        var invocations=new AtomicInteger();
        var registrations=new ArrayList<SkillExecutorRegistration>();
        for (String namespace: List.of("a", "b")) registrations.add(new SkillExecutorRegistration(
                new SkillIdentity(namespace, "fixture"), "", ai.loomspan.internal.security.SkillAccessPolicy.unrestricted(),
                new SkillSource(null, "fixture", "execute", null), invocation -> {invocations.incrementAndGet(); return "ok";}));
        when(processor.executors()).thenReturn(registrations);
        var properties=new LoomspanProperties();properties.getSkills().setLocations(List.of("classpath:/publication-no-files/*.yaml"));
        var manager=new SkillGenerationManager(processor,()->new YamlSkillCatalog(properties),new SkillInputContractResolver());
        manager.initializeStartup();
        var lifecycle=new FrameworkExecutionLifecycle(mock(ApplicationContext.class),Duration.ofMillis(100),Executors.newSingleThreadExecutor());
        var reloader=new DefaultSkillReloader(manager,lifecycle);var start=new CountDownLatch(1);
        var docs=List.of(new SkillDocument("executor","name: leaf\ndescription: leaf\nexecutor: fixture\n"));
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()){
            var a=workers.submit(()->{await(start);return reloader.prepare("a",docs,EMPTY);});
            var b=workers.submit(()->{await(start);return reloader.prepare("b",docs,EMPTY);});start.countDown();
            var first=a.get(5,TimeUnit.SECONDS);var second=b.get(5,TimeUnit.SECONDS);
            assertThat(invocations).hasValue(0);verify(processor,times(1)).capabilities();verify(processor,times(1)).executors();
            reloader.publish(first);reloader.publish(second);
            for (String namespace : List.of("a", "b")) {
                var capture=manager.capture(new SkillIdentity(namespace,"leaf"));
                try {
                    var binding=manager.validateAdmission(capture.snapshot()).executorBinding(namespace+"-leaf");
                    assertThat(binding.registration().invoker().apply(new SkillExecutorInvocation(Map.of(),Map.of(),binding.configuration(),namespace+"-leaf",binding.generationId()))).isEqualTo("ok");
                } finally { capture.lease().close(); }
            }
            assertThat(invocations).hasValue(2);
        } finally { lifecycle.destroy(); manager.destroy(); }
    }

    @Test void targetPublicationDuringBlockedPreparationRejectsItsOriginalCapturedBase() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var builds=new AtomicInteger();
        var f=new Fixture((p,policy)->{if(builds.incrementAndGet()==3){entered.countDown();await(release);}return runtime(p,policy);});
        var ready=f.reloader.prepare("default",List.of(),EMPTY);
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()){
            var pending=workers.submit(()->f.reloader.prepare("default",List.of(),EMPTY));
            try{assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();f.reloader.publish(ready);release.countDown();
                var stale=pending.get(5,TimeUnit.SECONDS);assertThatThrownBy(()->f.reloader.publish(stale)).hasMessageContaining("stale");
                assertThat(f.reloader.snapshot("default").generationId()).isEqualTo(ready.generationId());
            }finally{release.countDown();}
        }
    }

    @Test void retirementRegistrationHasNoReplayAndClosingDoesNotWaitForSelectedCallback() throws Exception {
        var f=new Fixture(null);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var retired=new CopyOnWriteArrayList<String>();
        var registration=f.reloader.onGenerationRetired(id->{entered.countDown();await(release);retired.add(id);});
        String old=f.reloader.snapshot("default").generationId();var candidate=f.reloader.prepare("default",List.of(),EMPTY);
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()){
            var publication=workers.submit(()->f.reloader.publish(candidate));
            try{assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
                workers.submit(()->{registration.close();return true;}).get(5,TimeUnit.SECONDS);
                release.countDown();publication.get(5,TimeUnit.SECONDS);assertThat(retired).containsExactly(old);
            }finally{release.countDown();}
        }
        var late=new CopyOnWriteArrayList<String>();f.reloader.onGenerationRetired(late::add);assertThat(late).isEmpty();
        var abandoned=f.reloader.prepare("default",List.of(),EMPTY);abandoned.close();assertThat(late).isEmpty();
        f.reloader.publish(f.reloader.prepare("default",List.of(),EMPTY));assertThat(late).containsExactly(candidate.generationId());assertThat(retired).containsExactly(old);
    }

    @Test void constructionFailureDoesNotCloseActiveOrConcurrentPreparedPeer() throws Exception {
        var resources=new CopyOnWriteArrayList<NamedAiConnectionRegistry>();
        var f=new Fixture((p,policy)->{
            if(p.getSession().getMaxDepth()==17)throw new IllegalStateException("candidate construction failure");
            var registry=mock(NamedAiConnectionRegistry.class);resources.add(registry);return new ExecutionRuntime(p,policy,registry);
        });
        var start=new CountDownLatch(1);
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()){
            var bad=workers.submit(()->{await(start);return f.reloader.prepare("bad",List.of(),new ExecutionConfiguration("loomspan: {session: {max-depth: 17}}"));});
            var good=workers.submit(()->{await(start);return f.reloader.prepare("good",List.of(),EMPTY);});
            start.countDown();var candidate=good.get(5,TimeUnit.SECONDS);
            assertThatThrownBy(()->bad.get(5,TimeUnit.SECONDS)).hasCauseInstanceOf(SkillReloadException.class);
            assertThat(resources).hasSize(2);verify(resources.get(0),never()).destroy();verify(resources.get(1),never()).destroy();
            f.reloader.publish(candidate);assertThat(f.reloader.snapshot("good").generationId()).isEqualTo(candidate.generationId());
            verify(resources.get(1),never()).destroy();
        }
    }

    static SkillDocument model(String name){return new SkillDocument(name,"name: "+name+"\ndescription: fixture\nmodel: model\n");}
    static SkillDocument calls(String name,String child){return new SkillDocument(name,model(name).yaml()+"allowed_skills: [{name: "+child+"}]\n");}
    static ExecutionRuntime runtime(LoomspanProperties p,TracePersistencePolicy policy){return new ExecutionRuntime(p,policy,mock(NamedAiConnectionRegistry.class));}
    static void await(CountDownLatch latch){try{if(!latch.await(5,TimeUnit.SECONDS))throw new AssertionError("gate timed out");}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new AssertionError(ex);}}
    static boolean publishAfter(CountDownLatch start,SkillReloader reloader,PreparedSkillUpdate c){await(start);try{reloader.publish(c);return true;}catch(SkillReloadException ex){assertThat(ex).hasMessageContaining("stale");return false;}}
    static class Fixture {
        final LoomspanProperties startup=new LoomspanProperties();final SkillGenerationManager manager;final FrameworkExecutionLifecycle lifecycle;final DefaultSkillReloader reloader;
        Fixture(BiFunction<LoomspanProperties,TracePersistencePolicy,ExecutionRuntime> factory){
            startup.getSkills().setLocations(List.of("classpath:/publication-no-files/*.yaml"));startup.getSession().setMaxDepth(91);
            var javaSkills=mock(SkillMethodBeanPostProcessor.class);when(javaSkills.capabilities()).thenReturn(List.of());
            manager=new SkillGenerationManager(javaSkills,()->new YamlSkillCatalog(startup),new SkillInputContractResolver(),startup,null,factory==null?SkillReloaderTest::runtime:factory);
            manager.initializeStartup();lifecycle=new FrameworkExecutionLifecycle(mock(ApplicationContext.class),Duration.ofMillis(100),Executors.newSingleThreadExecutor());
            reloader=new DefaultSkillReloader(manager,lifecycle);
        }
    }
}
