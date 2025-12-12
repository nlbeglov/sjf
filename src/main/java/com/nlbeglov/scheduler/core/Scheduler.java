package com.nlbeglov.scheduler.core;

import com.nlbeglov.scheduler.model.ScheduledProcess;

import java.util.List;

public interface Scheduler {

    void setConfig(SchedulerConfig config);

    void setProcesses(List<ScheduledProcess> processes);

    void reset();

    /**
     * Выполнить один шаг планирования для времени time.
     * Возвращает процесс, который выполняется на этом шаге (или null, если CPU простаивает).
     */
    ScheduledProcess step(int time);

    boolean isFinished();

    List<ScheduledProcess> getAllProcesses();
}