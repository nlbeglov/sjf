package com.nlbeglov.scheduler.model;

import java.util.List;

public class SimulationStats {

    private final double avgWaitingTime;
    private final double avgTurnaroundTime;
    private final int maxWaitingTime;
    private final int maxTurnaroundTime;

    public SimulationStats(double avgWaitingTime, double avgTurnaroundTime,
                           int maxWaitingTime, int maxTurnaroundTime) {
        this.avgWaitingTime = avgWaitingTime;
        this.avgTurnaroundTime = avgTurnaroundTime;
        this.maxWaitingTime = maxWaitingTime;
        this.maxTurnaroundTime = maxTurnaroundTime;
    }

    public double getAvgWaitingTime() {
        return avgWaitingTime;
    }

    public double getAvgTurnaroundTime() {
        return avgTurnaroundTime;
    }

    public int getMaxWaitingTime() {
        return maxWaitingTime;
    }

    public int getMaxTurnaroundTime() {
        return maxTurnaroundTime;
    }

    public static SimulationStats fromProcesses(List<ScheduledProcess> processes) {
        int n = processes.size();
        if (n == 0) {
            return new SimulationStats(0, 0, 0, 0);
        }
        int totalWait = 0;
        int totalTurn = 0;
        int maxWait = 0;
        int maxTurn = 0;
        for (ScheduledProcess p : processes) {
            int w = p.getWaitingTime();
            int t = p.getTurnaroundTime();
            totalWait += w;
            totalTurn += t;
            if (w > maxWait) maxWait = w;
            if (t > maxTurn) maxTurn = t;
        }
        return new SimulationStats(
                totalWait * 1.0 / n,
                totalTurn * 1.0 / n,
                maxWait,
                maxTurn
        );
    }
}
