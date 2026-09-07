package io.seoleir.engram.api.command;

public sealed interface Command permits CancelTimer, CompleteWorkflow, FailWorkflow, ScheduleActivity, StartTimer {

}
