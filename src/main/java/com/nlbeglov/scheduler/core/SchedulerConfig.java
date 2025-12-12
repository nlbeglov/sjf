package com.nlbeglov.scheduler.core;

public class SchedulerConfig {

    private final SchedulingMode mode;
    private final boolean agingEnabled;
    private final int agingInterval; // через сколько единиц ожидания повышать приоритет

    public SchedulerConfig(SchedulingMode mode, boolean agingEnabled, int agingInterval) {
        this.mode = mode;
        this.agingEnabled = agingEnabled;
        this.agingInterval = agingInterval;
    }

    public SchedulingMode getMode() {
        return mode;
    }

    public boolean isAgingEnabled() {
        return agingEnabled;
    }

    public int getAgingInterval() {
        return agingInterval;
    }

    public static SchedulerConfig defaultConfig() {
        return new SchedulerConfig(SchedulingMode.PREEMPTIVE, false, 5);
    }
}
