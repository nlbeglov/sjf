package com.nlbeglov.scheduler.sim;

import com.nlbeglov.scheduler.core.SJFWithPriorityScheduler;
import com.nlbeglov.scheduler.core.SchedulerConfig;
import com.nlbeglov.scheduler.core.SchedulingMode;
import com.nlbeglov.scheduler.model.ScheduledProcess;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimulationEngineTest {

    @Test
    void engineInvokesListenerAndStopsAfterCompletion() {
        SJFWithPriorityScheduler scheduler = new SJFWithPriorityScheduler();
        scheduler.setConfig(new SchedulerConfig(SchedulingMode.PREEMPTIVE, false, 5));
        scheduler.setProcesses(List.of(
                new ScheduledProcess("P1", 0, 2, 0),
                new ScheduledProcess("P2", 0, 1, 1)
        ));

        RecordingListener listener = new RecordingListener();
        SimulationEngine engine = new SimulationEngine(scheduler, listener);

        engine.start();
        for (int i = 0; i < 5; i++) {
            engine.step();
        }

        assertFalse(engine.isRunning(), "Движок должен остановиться после завершения процессов");
        assertEquals(3, listener.timeEvents.size(), "Слушатель должен получить событие на каждый такт");
        assertTrue(listener.finishedCalled, "Должен вызываться коллбек завершения");
    }

    private static class RecordingListener implements SimulationListener {
        private final List<Integer> timeEvents = new ArrayList<>();
        private boolean finishedCalled;

        @Override
        public void onTimeAdvanced(int time, ScheduledProcess runningProcess, List<ScheduledProcess> snapshot) {
            timeEvents.add(time);
        }

        @Override
        public void onLogEvent(String message) {
            // not needed for test
        }

        @Override
        public void onSimulationFinished() {
            finishedCalled = true;
        }
    }
}
