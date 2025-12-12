package com.nlbeglov.scheduler.core;

import com.nlbeglov.scheduler.model.ProcessState;
import com.nlbeglov.scheduler.model.ScheduledProcess;

import java.util.*;
import java.util.stream.Collectors;

public class SJFWithPriorityScheduler implements Scheduler {

    private SchedulerConfig config = SchedulerConfig.defaultConfig();

    private List<ScheduledProcess> allProcesses = new ArrayList<>();
    private ScheduledProcess current;
    private int time;
    private boolean finished;

    @Override
    public void setConfig(SchedulerConfig config) {
        this.config = config;
    }

    @Override
    public void setProcesses(List<ScheduledProcess> processes) {
        this.allProcesses = processes.stream()
                .sorted(Comparator.comparingInt(ScheduledProcess::getArrivalTime))
                .collect(Collectors.toList());
        reset();
    }

    @Override
    public void reset() {
        for (ScheduledProcess p : allProcesses) {
            // заново инициализируем runtime-часть
            p.setFinishTime(0); // временно, потом перезапишем
            p.setState(ProcessState.NEW);
            p.resetEffectivePriority();
        }
        // нам надо вернуть remainingTime и finishTime в исходное состояние,
        // но чтобы не городить отдельный reset в ScheduledProcess,
        // проще создать новые объекты сверху при новом запуске.
        // Для простоты решения считаем, что setProcesses вызывается перед reset,
        // а в UI для каждого нового запуска создается новый список процессов.
        current = null;
        time = 0;
        finished = false;
    }

    @Override
    public ScheduledProcess step(int time) {
        this.time = time;

        if (finished) return null;

        // перевести NEW → READY, если пришло их время
        for (ScheduledProcess p : allProcesses) {
            if (p.getState() == ProcessState.NEW && p.getArrivalTime() <= time) {
                p.setState(ProcessState.READY);
            }
        }

        // aging
        if (config.isAgingEnabled()) {
            applyAging();
        }

        // если текущий процесс есть и режим невытесняющий — просто выполняем его
        if (current != null && config.getMode() == SchedulingMode.NON_PREEMPTIVE) {
            executeCurrent();
            return current;
        }

        // иначе, выбираем лучший READY процесс
        ScheduledProcess next = chooseNextProcess();

        // если никого, кроме, возможно, текущего RUNNING, нет
        if (next == null && current == null) {
            // ничего не делаем, CPU idle
            checkFinished();
            return null;
        }

        // preemptive: сравниваем текущий и next
        if (config.getMode() == SchedulingMode.PREEMPTIVE) {
            if (shouldPreempt(current, next)) {
                if (current != null && current.getState() == ProcessState.RUNNING) {
                    current.setState(ProcessState.READY);
                }
                current = next;
                current.setStartTimeIfNotSet(time);
                current.setState(ProcessState.RUNNING);
            }
        } else {
            // non-preemptive: если current == null, просто берем next
            if (current == null && next != null) {
                current = next;
                current.setStartTimeIfNotSet(time);
                current.setState(ProcessState.RUNNING);
            }
        }

        // обновляем waitingTime для READY процессов
        for (ScheduledProcess p : allProcesses) {
            if (p.getState() == ProcessState.READY) {
                p.incrementWaitingTime();
            }
        }

        // выполняем текущий
        executeCurrent();

        checkFinished();
        return current;
    }

    private void executeCurrent() {
        if (current == null) return;
        current.decrementRemainingTime();
        if (current.isFinished()) {
            current.setFinishTime(time + 1);
            current.setState(ProcessState.FINISHED);
            current = null;
        }
    }

    private void applyAging() {
        for (ScheduledProcess p : allProcesses) {
            if (p.getState() == ProcessState.READY) {
                if (p.getAccumulatedWaitingTime() > 0 &&
                    p.getAccumulatedWaitingTime() % config.getAgingInterval() == 0) {
                    p.improvePriorityByAging();
                }
            }
        }
    }

    private ScheduledProcess chooseNextProcess() {
        return allProcesses.stream()
                .filter(p -> p.getState() == ProcessState.READY)
                .min((p1, p2) -> {
                    // меньше effectivePriority — выше приоритет
                    int cmpPr = Integer.compare(p1.getEffectivePriority(), p2.getEffectivePriority());
                    if (cmpPr != 0) return cmpPr;
                    // SJF: меньше remainingTime — лучше
                    int cmpRt = Integer.compare(p1.getRemainingTime(), p2.getRemainingTime());
                    if (cmpRt != 0) return cmpRt;
                    // tie-breaker: меньший arrivalTime
                    return Integer.compare(p1.getArrivalTime(), p2.getArrivalTime());
                })
                .orElse(null);
    }

    private boolean shouldPreempt(ScheduledProcess current, ScheduledProcess candidate) {
        if (candidate == null) {
            return false;
        }
        if (current == null) {
            return true;
        }
        // compare by effective priority
        if (candidate.getEffectivePriority() < current.getEffectivePriority()) {
            return true;
        }
        if (candidate.getEffectivePriority() > current.getEffectivePriority()) {
            return false;
        }
        // same priority: SJF
        return candidate.getRemainingTime() < current.getRemainingTime();
    }

    private void checkFinished() {
        finished = allProcesses.stream().allMatch(ScheduledProcess::isFinished);
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public List<ScheduledProcess> getAllProcesses() {
        return allProcesses;
    }
}