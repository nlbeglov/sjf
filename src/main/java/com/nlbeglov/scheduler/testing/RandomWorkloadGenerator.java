package com.nlbeglov.scheduler.testing;

import com.nlbeglov.scheduler.model.ScheduledProcess;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RandomWorkloadGenerator {

    private final Random random = new Random();

    public List<ScheduledProcess> generate(int count,
                                           int maxArrival,
                                           int minBurst, int maxBurst,
                                           int minPriority, int maxPriority) {
        List<ScheduledProcess> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int arrival = random.nextInt(maxArrival + 1);
            int burst = minBurst + random.nextInt(maxBurst - minBurst + 1);
            int priority = minPriority + random.nextInt(maxPriority - minPriority + 1);
            String id = "P" + (i + 1);
            list.add(new ScheduledProcess(id, arrival, burst, priority));
        }
        return list;
    }
}