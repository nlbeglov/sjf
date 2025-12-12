package com.nlbeglov.scheduler.core;

import com.nlbeglov.scheduler.model.ScheduledProcess;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SJFWithPrioritySchedulerTest {

    @Test
    void preemptiveSchedulerSwitchesToHigherPriorityProcess() {
        SJFWithPriorityScheduler scheduler = new SJFWithPriorityScheduler();
        scheduler.setConfig(new SchedulerConfig(SchedulingMode.PREEMPTIVE, false, 5));
        scheduler.setProcesses(List.of(
                new ScheduledProcess("A", 0, 5, 2),
                new ScheduledProcess("B", 1, 3, 0)
        ));

        ScheduledProcess time0 = scheduler.step(0);
        ScheduledProcess time1 = scheduler.step(1);

        assertNotNull(time0);
        assertEquals("A", time0.getId());
        assertNotNull(time1);
        assertEquals("B", time1.getId(), "Более высокий приоритет должен вытеснить текущий процесс");
    }

    @Test
    void nonPreemptiveSchedulerKeepsCurrentProcess() {
        SJFWithPriorityScheduler scheduler = new SJFWithPriorityScheduler();
        scheduler.setConfig(new SchedulerConfig(SchedulingMode.NON_PREEMPTIVE, false, 5));
        scheduler.setProcesses(List.of(
                new ScheduledProcess("A", 0, 4, 1),
                new ScheduledProcess("B", 1, 2, 0)
        ));

        ScheduledProcess time0 = scheduler.step(0);
        ScheduledProcess time1 = scheduler.step(1);

        assertNotNull(time0);
        assertEquals("A", time0.getId());
        assertNotNull(time1);
        assertEquals("A", time1.getId(), "В невытесняющем режиме процесс не должен меняться");
    }

    @Test
    void agingImprovesPriorityAndAllowsPreemption() {
        SJFWithPriorityScheduler scheduler = new SJFWithPriorityScheduler();
        scheduler.setConfig(new SchedulerConfig(SchedulingMode.PREEMPTIVE, true, 1));
        scheduler.setProcesses(List.of(
                new ScheduledProcess("LONG", 0, 5, 2),
                new ScheduledProcess("WAITING", 0, 2, 5)
        ));

        scheduler.step(0); // LONG
        scheduler.step(1); // LONG, WAITING priority -> 4
        scheduler.step(2); // LONG, WAITING priority -> 3
        ScheduledProcess time3 = scheduler.step(3); // WAITING priority -> 2, должно вытеснить по SJF

        assertNotNull(time3);
        assertEquals("WAITING", time3.getId(), "Старение должно привести к вытеснению длинного процесса");
    }
}
