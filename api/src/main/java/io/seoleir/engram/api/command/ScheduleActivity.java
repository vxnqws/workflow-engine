package io.seoleir.engram.api.command;

public record ScheduleActivity(String activityType, byte[] input) implements Command {

}
