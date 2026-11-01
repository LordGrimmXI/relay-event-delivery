package com.relay.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DeliverySchedulerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DeliveryScheduler.class)
            .withBean(DeliveryWorker.class, () -> mock(DeliveryWorker.class));

    @Test
    void tickRunsOneWorkerPass() {
        DeliveryWorker worker = mock(DeliveryWorker.class);
        DeliveryScheduler scheduler = new DeliveryScheduler(worker);

        scheduler.tick();

        verify(worker, times(1)).runOnce();
    }

    @Test
    void aFailingPassDoesNotEscapeTheScheduler() {
        DeliveryWorker worker = mock(DeliveryWorker.class);
        when(worker.runOnce()).thenThrow(new IllegalStateException("database down"));
        DeliveryScheduler scheduler = new DeliveryScheduler(worker);

        assertThatCode(() -> scheduler.tick()).doesNotThrowAnyException();
        verify(worker, times(1)).runOnce();
    }

    @Test
    void schedulerIsNotCreatedByDefault() {
        runner.run(context -> assertThat(context).doesNotHaveBean(DeliveryScheduler.class));
    }

    @Test
    void schedulerIsCreatedWhenEnabled() {
        runner.withPropertyValues("relay.worker.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(DeliveryScheduler.class));
    }
}