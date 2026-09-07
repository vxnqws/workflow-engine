package io.seoleir.engram.api.command;

import java.time.Duration;

public record StartTimer(String timerId, Duration delay) implements Command {
}
