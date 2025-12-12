package com.nlbeglov.scheduler.sim;

import com.nlbeglov.scheduler.core.Scheduler;
import com.nlbeglov.scheduler.model.ScheduledProcess;

import java.util.ArrayList;
import java.util.List;

public class SimulationEngine {

    private final Scheduler scheduler;
    private final SimulationListener listener;

    private int time;
    private boolean running;

    public SimulationEngine(Scheduler scheduler, SimulationListener listener) {
        this.scheduler = scheduler;
        this.listener = listener;
        this.time = 0;
        this.running = false;
    }

    public void reset() {
        time = 0;
        running = false;
        scheduler.reset();
    }

    public void start() {
        running = true;
    }

    public void pause() {
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * Один шаг симуляции. Вызывается из JavaFX Timeline.
     */
    public void step() {
        if (!running) return;
        if (scheduler.isFinished()) {
            running = false;
            listener.onSimulationFinished();
            return;
        }
        ScheduledProcess runningProcess = scheduler.step(time);
        List<ScheduledProcess> snapshot = new ArrayList<>(scheduler.getAllProcesses());
        listener.onTimeAdvanced(time, runningProcess, snapshot);
        time++;
    }

    public int getTime() {
        return time;
    }
}