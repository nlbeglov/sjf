package com.nlbeglov.scheduler.testing;

import com.nlbeglov.scheduler.core.SJFWithPriorityScheduler;
import com.nlbeglov.scheduler.core.SchedulerConfig;
import com.nlbeglov.scheduler.core.SchedulingMode;
import com.nlbeglov.scheduler.model.ScheduledProcess;
import com.nlbeglov.scheduler.model.SimulationStats;

import java.util.ArrayList;
import java.util.List;

public class LoadTester {

    private final RandomWorkloadGenerator generator = new RandomWorkloadGenerator();

    public SimulationStats runSingle(List<ScheduledProcess> processes,
                                     SchedulerConfig config) {
        SJFWithPriorityScheduler scheduler = new SJFWithPriorityScheduler();
        scheduler.setConfig(config);
        scheduler.setProcesses(cloneProcesses(processes));

        int time = 0;
        while (!scheduler.isFinished()) {
            scheduler.step(time);
            time++;
        }
        return SimulationStats.fromProcesses(scheduler.getAllProcesses());
    }

    public List<SimulationStats> runMany(int runs,
                                         int processCount,
                                         int maxArrival,
                                         int minBurst, int maxBurst,
                                         int minPriority, int maxPriority,
                                         SchedulerConfig config) {
        List<SimulationStats> stats = new ArrayList<>();
        for (int i = 0; i < runs; i++) {
            List<ScheduledProcess> set = generator.generate(
                    processCount, maxArrival, minBurst, maxBurst, minPriority, maxPriority
            );
            stats.add(runSingle(set, config));
        }
        return stats;
    }

    private List<ScheduledProcess> cloneProcesses(List<ScheduledProcess> src) {
        List<ScheduledProcess> res = new ArrayList<>();
        for (ScheduledProcess p : src) {
            res.add(new ScheduledProcess(p.getId(), p.getArrivalTime(), p.getBurstTime(), p.getBasePriority()));
        }
        return res;
    }
}