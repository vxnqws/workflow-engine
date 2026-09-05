package io.seoleir.engram.runtime.registry;

import io.seoleir.engram.core.api.state.WorkflowState;

import java.util.Optional;
import java.util.Set;

public interface Registry {

    <S extends WorkflowState> WorkflowDefinition<S> register(Class<?> workflowClass, Object instance, S initialState);

    <S extends WorkflowState> WorkflowDefinition<S> register(Class<?> workflowClass, S initialState);

    void freeze();

    WorkflowDefinition<? extends WorkflowState> definitionFor(String type);

    Set<String> registeredTypes();
}
