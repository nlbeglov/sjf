package com.nlbeglov.scheduler.sim;

import com.nlbeglov.scheduler.model.ScheduledProcess;

import java.util.List;

public interface SimulationListener {

    void onTimeAdvanced(int time, ScheduledProcess runningProcess, List<ScheduledProcess> snapshot);

    void onLogEvent(String message);

    void onSimulationFinished();
}
