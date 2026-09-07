package io.seoleir.engram.api.command;

public record FailWorkflow(String reason, String details) implements Command {
}
