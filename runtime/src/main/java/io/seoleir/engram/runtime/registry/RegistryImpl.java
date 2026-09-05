package io.seoleir.engram.runtime.registry;

import io.seoleir.engram.core.api.state.WorkflowState;
import io.seoleir.engram.core.internal.codec.StateCodec;
import io.seoleir.engram.runtime.exception.RegistrationException;

import java.util.*;

public class RegistryImpl implements Registry {

    private final StateCodec codec;
    private Map<String, WorkflowDefinition<? extends WorkflowState>> definitions = new LinkedHashMap<>();
    private volatile boolean frozen;

    public RegistryImpl(StateCodec codec) {
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    @Override
    public <S extends WorkflowState> WorkflowDefinition<S> register(Class<?> workflowClass, Object instance, S initialState) {
        if (frozen) {
            throw new IllegalStateException("Registry is frozen; register before startup");
        }

        Objects.requireNonNull(workflowClass, "workflowClass");
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(initialState, "initialState");

        WorkflowDefinition<S> definition = WorkflowDefinitionParser.parse(workflowClass, instance, initialState, codec);

        var previous = definitions.putIfAbsent(definition.type(), definition);
        if (previous != null) {
            throw new RegistrationException(
                    "Workflow type '" + definition.type() + "' is already registered by "
                            + previous.target().getClass().getName()
                            + "; conflicting class: " + workflowClass.getName());
        }
        return definition;
    }

    @Override
    public <S extends WorkflowState> WorkflowDefinition<S> register(Class<?> workflowClass, S initialState) {
        return register(workflowClass, instantiate(workflowClass), initialState);
    }

    public void freeze() {
        if (definitions.isEmpty()) {
            throw new IllegalStateException("No workflows registered");
        }
        this.definitions = Map.copyOf(definitions);
        frozen = true;
    }

    public WorkflowDefinition<? extends WorkflowState> definitionFor(String type) {
        return definitions.get(type);
    }

    public Set<String> registeredTypes() {
        return definitions.keySet();
    }

    private static Object instantiate(Class<?> workflowClass) {
        try {
            return workflowClass.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException e) {
            throw new RegistrationException(
                    workflowClass.getName() + " must have a public no-argument constructor "
                            + "to be registered by scanning; register it explicitly otherwise", e);
        } catch (ReflectiveOperationException e) {
            throw new RegistrationException(
                    "Failed to instantiate " + workflowClass.getName(), e);
        }
    }
}
