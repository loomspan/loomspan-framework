package ai.loomspan.internal.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.DefaultLifecycleProcessor;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.runtime.observation.NoOpExecutionObservationHandleFactory;
import ai.loomspan.internal.runtime.trace.ImmediateCompletionRetention;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class FrameworkShutdownIntegrationTest
{
    @Test
    void parentAndChildCloseEventsOnlyCloseTheirOwningLifecycle()
    {
        var parent = new AnnotationConfigApplicationContext();
        parent.refresh();
        var child = new AnnotationConfigApplicationContext();
        child.setParent(parent);
        child.refresh();
        var parentExecutor = Executors.newVirtualThreadPerTaskExecutor();
        var childExecutor = Executors.newVirtualThreadPerTaskExecutor();
        var parentLifecycle = new FrameworkExecutionLifecycle(parent, Duration.ofMillis(100), parentExecutor);
        var childLifecycle = new FrameworkExecutionLifecycle(child, Duration.ofMillis(100), childExecutor);

        parentLifecycle.onApplicationEvent(new ContextClosedEvent(child));
        childLifecycle.onApplicationEvent(new ContextClosedEvent(parent));

        parentLifecycle.admitRoot().close();
        childLifecycle.admitRoot().close();
        parentLifecycle.destroy();
        childLifecycle.destroy();
        child.close();
        parent.close();
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void closeEventRejectsLateRootBeforeSessionConstruction()
    {
        var context = new AnnotationConfigApplicationContext();
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        var lifecycle = new FrameworkExecutionLifecycle(context, Duration.ofMillis(100), executor);
        var observationCreations = new AtomicInteger();
        var actionCalled = new AtomicBoolean();
        var codecs = LoomspanJacksonCodecs.defaults();
        var runner = new LoomspanSessionRunner(
                3,
                TracePersistencePolicy.ONERROR,
                Clock.systemUTC(),
                (sessionId, entrySkill) -> {
                    observationCreations.incrementAndGet();
                    return NoOpExecutionObservationHandleFactory.INSTANCE.create(sessionId, entrySkill);
                },
                ImmediateCompletionRetention.INSTANCE,
                new LoomspanProperties.Session.Quotas(),
                codecs.canonicalTrace(),
                lifecycle);
        context.addApplicationListener(lifecycle);
        context.refresh();
        var admitted = lifecycle.admitRoot();

        context.publishEvent(new ContextClosedEvent(context));

        assertThatThrownBy(() -> runner.callWithNewSession("late", session -> {
            actionCalled.set(true);
            return "unreachable";
        })).isInstanceOf(RejectedExecutionException.class);
        assertThat(observationCreations).hasValue(0);
        assertThat(actionCalled).isFalse();
        admitted.close();
        lifecycle.stop();
        lifecycle.destroy();
        context.close();
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void standardAsynchronousMulticasterStillClosesFrameworkAdmissionSynchronously()
    {
        var context = new AnnotationConfigApplicationContext();
        var eventExecutor = Executors.newSingleThreadExecutor();
        var missionExecutor = Executors.newVirtualThreadPerTaskExecutor();
        var multicaster = new SimpleApplicationEventMulticaster();
        multicaster.setTaskExecutor(eventExecutor);
        context.getBeanFactory().registerSingleton(
                AnnotationConfigApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME, multicaster);
        var lifecycle = new FrameworkExecutionLifecycle(context, Duration.ofMillis(100), missionExecutor);
        context.addApplicationListener(lifecycle);
        context.refresh();

        context.publishEvent(new ContextClosedEvent(context));

        assertThatThrownBy(lifecycle::admitRoot).isInstanceOf(RejectedExecutionException.class);
        lifecycle.stop();
        lifecycle.destroy();
        eventExecutor.shutdownNow();
        context.close();
    }

    @Test
    void independentCloseListenersWorkInEitherRegistrationOrder()
    {
        verifyIndependentListeners(true);
        verifyIndependentListeners(false);
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void skippedOrFailedEventDeliveryStillUsesNonwaitingFallback()
    {
        var context = new AnnotationConfigApplicationContext();
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        var lifecycle = new FrameworkExecutionLifecycle(context, Duration.ofSeconds(1), executor);
        var admitted = lifecycle.admitRoot();

        lifecycle.destroy();

        assertThatThrownBy(lifecycle::admitRoot).isInstanceOf(RejectedExecutionException.class);
        assertThat(executor.isShutdown()).isTrue();
        admitted.close();
        lifecycle.destroy();
        context.close();
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void failedStartupDestroysTheLifecycleWithoutWaiting()
    {
        var context = new AnnotationConfigApplicationContext();
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        context.registerBean(FrameworkExecutionLifecycle.class,
                () -> new FrameworkExecutionLifecycle(context, Duration.ofSeconds(1), executor));
        context.registerBean(SmartInitializingSingleton.class,
                () -> () -> { throw new IllegalStateException("startup failed"); });

        assertThatThrownBy(context::refresh).isInstanceOf(RuntimeException.class);

        assertThat(executor.isShutdown()).isTrue();
        context.close();
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void throwingCloseListenerCannotSkipLifecycleStopFallback()
    {
        var context = new AnnotationConfigApplicationContext();
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        var lifecycle = new FrameworkExecutionLifecycle(context, Duration.ofMillis(25), executor);
        context.getBeanFactory().registerSingleton("frameworkExecutionLifecycle", lifecycle);
        context.addApplicationListener((ContextClosedEvent event) -> {
            throw new IllegalStateException("listener failed");
        });
        context.addApplicationListener(lifecycle);
        context.refresh();

        context.close();

        assertThatThrownBy(lifecycle::admitRoot).isInstanceOf(RejectedExecutionException.class);
        assertThat(executor.isShutdown()).isTrue();
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void shorterSpringPhaseTimeoutDoesNotTruncateFrameworkWaitAndKeepsLowerPhaseResourceRunning()
            throws Exception
    {
        var context = new AnnotationConfigApplicationContext();
        var missionExecutor = Executors.newVirtualThreadPerTaskExecutor();
        var lifecycle = new FrameworkExecutionLifecycle(context, Duration.ofSeconds(1), missionExecutor);
        var requiredResource = new RecordingLifecycle();
        context.getBeanFactory().registerSingleton("frameworkExecutionLifecycle", lifecycle);
        context.getBeanFactory().registerSingleton("requiredResource", requiredResource);
        context.addApplicationListener(lifecycle);
        context.refresh();
        ((DefaultLifecycleProcessor) context.getBean(
                AbstractApplicationContext.LIFECYCLE_PROCESSOR_BEAN_NAME))
                .setTimeoutPerShutdownPhase(10L);
        var admitted = lifecycle.admitRoot();

        Thread closeThread = Thread.ofVirtual().start(context::close);
        long eventDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (lifecycle.deadlineNanos() == Long.MAX_VALUE && System.nanoTime() < eventDeadline)
            Thread.onSpinWait();
        assertThat(lifecycle.deadlineNanos()).isNotEqualTo(Long.MAX_VALUE);
        Thread.sleep(50L);
        assertThat(closeThread.isAlive()).isTrue();
        assertThat(requiredResource.running.get()).isTrue();

        admitted.close();
        closeThread.join(Duration.ofSeconds(1));
        assertThat(closeThread.isAlive()).isFalse();
        assertThat(requiredResource.running.get()).isFalse();
    }

    private static final class RecordingLifecycle implements SmartLifecycle
    {
        private final AtomicBoolean running = new AtomicBoolean();

        @Override public void start() { running.set(true); }
        @Override public void stop() { running.set(false); }
        @Override public boolean isRunning() { return running.get(); }
        @Override public int getPhase() { return 0; }
    }

    private static void verifyIndependentListeners(boolean hostFirst)
    {
        var context = new AnnotationConfigApplicationContext();
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        var lifecycle = new FrameworkExecutionLifecycle(context, Duration.ofMillis(100), executor);
        var hostGateClosed = new AtomicBoolean();
        var eventReturns = new AtomicInteger();
        ApplicationListener<ContextClosedEvent> hostGate = event -> {
            hostGateClosed.set(true);
            eventReturns.incrementAndGet();
        };
        if (hostFirst)
        {
            context.addApplicationListener(hostGate);
            context.addApplicationListener(lifecycle);
        }
        else
        {
            context.addApplicationListener(lifecycle);
            context.addApplicationListener(hostGate);
        }
        context.refresh();
        var admitted = lifecycle.admitRoot();

        context.publishEvent(new ContextClosedEvent(context));

        assertThat(hostGateClosed).isTrue();
        assertThat(eventReturns).hasValue(1);
        assertThatThrownBy(lifecycle::admitRoot).isInstanceOf(RejectedExecutionException.class);
        admitted.close();
        lifecycle.stop();
        lifecycle.destroy();
        context.close();
    }
}
