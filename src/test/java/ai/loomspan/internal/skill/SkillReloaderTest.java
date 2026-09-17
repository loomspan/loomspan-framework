package ai.loomspan.internal.skill;

import ai.loomspan.api.PreparedSkillUpdate;
import ai.loomspan.api.SkillReloadException;
import ai.loomspan.api.SkillDocument;
import ai.loomspan.internal.core.FrameworkExecutionLifecycle;
import ai.loomspan.internal.core.SkillMethodBeanPostProcessor;
import ai.loomspan.internal.runtime.input.SkillInputContractResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.ApplicationContext;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillReloaderTest
{
    @Test
    void suppliedCandidatesUseTheSameOwnerBaseOneShotAndShutdownChecks()
    {
        SkillGenerationManager manager = manager(SkillReloaderTest::emptyCatalog);
        manager.afterSingletonsInstantiated();
        FrameworkExecutionLifecycle lifecycle = lifecycle();
        DefaultSkillReloader reloader = new DefaultSkillReloader(manager, lifecycle);
        DefaultSkillReloader foreign = new DefaultSkillReloader(manager, lifecycle);
        List<SkillDocument> documents = List.of(new SkillDocument("label", "name: skill\n"));
        PreparedSkillUpdate first = reloader.prepare(documents);
        PreparedSkillUpdate competing = reloader.prepare();
        assertThatThrownBy(() -> foreign.publish(first)).isInstanceOf(SkillReloadException.class);
        reloader.publish(first);
        assertThatThrownBy(() -> reloader.publish(first)).isInstanceOf(SkillReloadException.class);
        assertThatThrownBy(() -> reloader.publish(competing)).isInstanceOf(SkillReloadException.class)
                .hasMessageContaining("stale");
        lifecycle.closeAdmission();
        assertThatThrownBy(() -> reloader.prepare(documents)).isInstanceOf(SkillReloadException.class);
    }

    @Test
    void prepareIsDetachedAndPublicationChecksBaseOwnerAndOneShot()
    {
        AtomicInteger reads = new AtomicInteger();
        SkillGenerationManager manager = manager(() -> { reads.incrementAndGet(); return emptyCatalog(); });
        manager.afterSingletonsInstantiated();
        FrameworkExecutionLifecycle lifecycle = lifecycle();
        DefaultSkillReloader reloader = new DefaultSkillReloader(manager, lifecycle);
        DefaultSkillReloader foreign = new DefaultSkillReloader(manager, lifecycle);
        String initial = reloader.snapshot().generationId();

        PreparedSkillUpdate first = reloader.prepare();
        PreparedSkillUpdate second = reloader.prepare();
        assertThat(first.generationId()).isNotEqualTo(initial).isNotEqualTo(second.generationId());
        assertThat(first.snapshot().generationId()).isEqualTo(first.generationId());
        assertThat(reloader.snapshot().generationId()).isEqualTo(initial);
        assertThatThrownBy(() -> foreign.publish(first)).isInstanceOf(SkillReloadException.class);
        PreparedSkillUpdate fabricated = new PreparedSkillUpdate()
        {
            @Override public String generationId() { return first.generationId(); }
            @Override public ai.loomspan.api.SkillCatalog snapshot() { return first.snapshot(); }
        };
        assertThatThrownBy(() -> reloader.publish(fabricated)).isInstanceOf(SkillReloadException.class);

        reloader.publish(first);
        assertThat(reloader.snapshot().generationId()).isEqualTo(first.generationId());
        assertThatThrownBy(() -> reloader.publish(first)).isInstanceOf(SkillReloadException.class);
        assertThatThrownBy(() -> reloader.publish(second)).isInstanceOf(SkillReloadException.class)
                .hasMessageContaining("stale");
        assertThat(reads).hasValue(3); // Startup and two preparations; snapshot/publish never read.
        lifecycle.closeAdmission();
        assertThatThrownBy(reloader::prepare).isInstanceOf(SkillReloadException.class);
        assertThatThrownBy(() -> reloader.publish(second)).isInstanceOf(SkillReloadException.class);
    }

    @Test
    void publicationDuringSlowPreparationMakesItsCapturedBaseStale() throws Exception
    {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        AtomicInteger reads = new AtomicInteger();
        SkillGenerationManager manager = manager(() -> {
            if (reads.incrementAndGet() == 3)
            {
                entered.countDown();
                await(proceed);
            }
            return emptyCatalog();
        });
        manager.afterSingletonsInstantiated();
        DefaultSkillReloader reloader = new DefaultSkillReloader(manager, lifecycle());
        PreparedSkillUpdate ready = reloader.prepare();
        ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        try
        {
            var pending = workers.submit(() -> reloader.prepare(List.of(new SkillDocument("slow supplied", "name: skill\n"))));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(reloader.snapshot().generationId()).isNotEqualTo(ready.generationId());
            reloader.publish(ready);
            proceed.countDown();
            PreparedSkillUpdate stale = pending.get(5, TimeUnit.SECONDS);
            assertThatThrownBy(() -> reloader.publish(stale)).isInstanceOf(SkillReloadException.class)
                    .hasMessageContaining("stale");
        }
        finally
        {
            proceed.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void preparationsSerializeWithoutBlockingSnapshots() throws Exception
    {
        CountDownLatch firstReadEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstRead = new CountDownLatch(1);
        CountDownLatch secondReadEntered = new CountDownLatch(1);
        AtomicInteger reads = new AtomicInteger();
        SkillGenerationManager manager = manager(() -> {
            int read = reads.incrementAndGet();
            if (read == 2)
            {
                firstReadEntered.countDown();
                await(releaseFirstRead);
            }
            if (read == 3) secondReadEntered.countDown();
            return emptyCatalog();
        });
        manager.afterSingletonsInstantiated();
        DefaultSkillReloader reloader = new DefaultSkillReloader(manager, lifecycle());
        String activeId = reloader.snapshot().generationId();
        ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        try
        {
            Future<PreparedSkillUpdate> first = workers.submit(() -> reloader.prepare());
            assertThat(firstReadEntered.await(5, TimeUnit.SECONDS)).isTrue();
            CountDownLatch secondStarted = new CountDownLatch(1);
            AtomicReference<Thread> secondWorker = new AtomicReference<>();
            Future<PreparedSkillUpdate> second = workers.submit(() -> {
                secondWorker.set(Thread.currentThread());
                secondStarted.countDown();
                return reloader.prepare();
            });
            assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
            long waitDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (secondWorker.get().getState() != Thread.State.BLOCKED
                    && !second.isDone() && System.nanoTime() < waitDeadline)
                Thread.onSpinWait();
            assertThat(secondWorker.get().getState()).isEqualTo(Thread.State.BLOCKED);
            assertThat(secondReadEntered.getCount()).isOne();
            assertThat(reloader.snapshot().generationId()).isEqualTo(activeId);
            releaseFirstRead.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).generationId())
                    .isNotEqualTo(second.get(5, TimeUnit.SECONDS).generationId());
            assertThat(reads).hasValue(3);
        }
        finally
        {
            releaseFirstRead.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void competingPublicationsFromOneBaseHaveExactlyOneWinner() throws Exception
    {
        SkillGenerationManager manager = manager(SkillReloaderTest::emptyCatalog);
        manager.afterSingletonsInstantiated();
        DefaultSkillReloader reloader = new DefaultSkillReloader(manager, lifecycle());
        PreparedSkillUpdate first = reloader.prepare();
        PreparedSkillUpdate second = reloader.prepare();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        try
        {
            Future<Boolean> firstPublished = workers.submit(() -> publishAfter(start, reloader, first));
            Future<Boolean> secondPublished = workers.submit(() -> publishAfter(start, reloader, second));
            start.countDown();
            assertThat(List.of(firstPublished.get(5, TimeUnit.SECONDS), secondPublished.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(reloader.snapshot().generationId()).isIn(first.generationId(), second.generationId());
        }
        finally
        {
            start.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void shutdownDuringPreparationCannotReturnPublishableSuccess() throws Exception
    {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        AtomicInteger reads = new AtomicInteger();
        SkillGenerationManager manager = manager(() -> {
            if (reads.incrementAndGet() == 2)
            {
                entered.countDown();
                await(proceed);
            }
            return emptyCatalog();
        });
        manager.afterSingletonsInstantiated();
        FrameworkExecutionLifecycle lifecycle = lifecycle();
        DefaultSkillReloader reloader = new DefaultSkillReloader(manager, lifecycle);
        ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        try
        {
            var pending = workers.submit(() -> reloader.prepare());
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            lifecycle.closeAdmission();
            proceed.countDown();
            assertThatThrownBy(() -> pending.get(5, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(SkillReloadException.class);
        }
        finally
        {
            proceed.countDown();
            workers.shutdownNow();
        }
    }

    private static SkillGenerationManager manager(java.util.function.Supplier<YamlSkillCatalog> catalog)
    {
        SkillMethodBeanPostProcessor javaSkills = mock(SkillMethodBeanPostProcessor.class);
        when(javaSkills.capabilities()).thenReturn(List.of());
        return new SkillGenerationManager(javaSkills, catalog, new SkillInputContractResolver(),
                new StaticListableBeanFactory());
    }

    private static YamlSkillCatalog emptyCatalog()
    {
        YamlSkillCatalog catalog = mock(YamlSkillCatalog.class);
        when(catalog.getSkills()).thenReturn(List.of());
        return catalog;
    }

    private static FrameworkExecutionLifecycle lifecycle()
    {
        return new FrameworkExecutionLifecycle(mock(ApplicationContext.class), Duration.ofSeconds(5),
                mock(ExecutorService.class));
    }

    private static void await(CountDownLatch latch)
    {
        try
        {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for test gate");
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }

    private static boolean publishAfter(CountDownLatch start, DefaultSkillReloader reloader,
            PreparedSkillUpdate update)
    {
        await(start);
        try { reloader.publish(update); return true; }
        catch (SkillReloadException expected) { return false; }
    }
}
