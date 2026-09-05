package io.seoleir.engram.client;

import io.seoleir.engram.core.api.state.WorkflowState;

import java.util.Objects;

public record WorkflowRegistration<S extends WorkflowState>(
        Class<?> workflowClass,
        Object instance,
        S initialState
) {
    public WorkflowRegistration {
        Objects.requireNonNull(workflowClass, "workflowClass");
        Objects.requireNonNull(initialState, "initialState");
    }
}