package com.nlbeglov.scheduler.model;

public class ScheduledProcess {

    private final String id;
    private final int arrivalTime;
    private final int burstTime;
    private final int basePriority; // чем меньше, тем важнее

    private int remainingTime;
    private int effectivePriority; // с учетом aging
    private ProcessState state;

    private Integer startTime;
    private Integer finishTime;

    private int accumulatedWaitingTime;

    public ScheduledProcess(String id, int arrivalTime, int burstTime, int basePriority) {
        this.id = id;
        this.arrivalTime = arrivalTime;
        this.burstTime = burstTime;
        this.basePriority = basePriority;
        this.remainingTime = burstTime;
        this.effectivePriority = basePriority;
        this.state = ProcessState.NEW;
    }

    public String getId() {
        return id;
    }

    public int getArrivalTime() {
        return arrivalTime;
    }

    public int getBurstTime() {
        return burstTime;
    }

    public int getBasePriority() {
        return basePriority;
    }

    public int getRemainingTime() {
        return remainingTime;
    }

    public void decrementRemainingTime() {
        if (remainingTime > 0) {
            remainingTime--;
        }
    }

    public int getEffectivePriority() {
        return effectivePriority;
    }

    public void improvePriorityByAging() {
        if (effectivePriority > 0) {
            effectivePriority--;
        }
    }

    public void resetEffectivePriority() {
        this.effectivePriority = basePriority;
    }

    public ProcessState getState() {
        return state;
    }

    public void setState(ProcessState state) {
        this.state = state;
    }

    public Integer getStartTime() {
        return startTime;
    }

    public void setStartTimeIfNotSet(int time) {
        if (this.startTime == null) {
            this.startTime = time;
        }
    }

    public Integer getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(int finishTime) {
        this.finishTime = finishTime;
    }

    public int getWaitingTime() {
        if (finishTime == null) {
            return 0;
        }
        return (finishTime - arrivalTime) - burstTime;
    }

    public int getTurnaroundTime() {
        if (finishTime == null) {
            return 0;
        }
        return finishTime - arrivalTime;
    }

    public int getAccumulatedWaitingTime() {
        return accumulatedWaitingTime;
    }

    public void incrementWaitingTime() {
        this.accumulatedWaitingTime++;
    }

    public boolean isFinished() {
        return remainingTime <= 0;
    }
}