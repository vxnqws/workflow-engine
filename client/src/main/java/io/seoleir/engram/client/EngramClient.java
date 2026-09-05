package io.seoleir.engram.client;

import io.seoleir.engram.core.api.state.WorkflowState;
import io.seoleir.engram.core.internal.codec.StateCodec;
import io.seoleir.engram.runtime.adapter.AnnotatedDecider;
import io.seoleir.engram.runtime.coordinator.Coordinator;
import io.seoleir.engram.runtime.registry.Registry;
import io.seoleir.engram.runtime.registry.RegistryImpl;
import io.seoleir.engram.runtime.registry.WorkflowDefinition;
import io.seoleir.engram.spi.EventLog;
import io.seoleir.engram.spi.StateStore;

import java.util.*;

public final class EngramClient {

    private final StateCodec codec;
    private final EventLog eventLog;
    private final StateStore stateStore;
    private final Registry registry;
    private final Map<String, Coordinator<?>> coordinators;

    EngramClient(StateCodec codec, EventLog eventLog, StateStore stateStore,
                 Registry registry, Map<String, Coordinator<?>> coordinators) {
        this.codec = codec;
        this.eventLog = eventLog;
        this.stateStore = stateStore;
        this.registry = registry;
        this.coordinators = coordinators;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Registry registry() {
        return registry;
    }

    public List<String> handle(String workflowType, String workflowId,
                               String eventType, Object payload) {
        Coordinator<?> coordinator = coordinatorFor(workflowType);
        return coordinator.handle(workflowId, eventType, codec.encode(payload));
    }

    Coordinator<?> coordinatorFor(String workflowType) {
        var coordinator = coordinators.get(workflowType);
        if (coordinator == null) {
            throw new IllegalArgumentException(
                    "Unknown workflow type '" + workflowType
                            + "'; registered: " + coordinators.keySet());
        }
        return coordinator;
    }

    public static final class Builder {

        private StateCodec codec;
        private EventLog eventLog;
        private StateStore stateStore;
        private final List<WorkflowRegistration<?>> workflows = new ArrayList<>();

        public Builder codec(StateCodec codec) {
            this.codec = codec;
            return this;
        }

        public Builder eventLog(EventLog eventLog) {
            this.eventLog = eventLog;
            return this;
        }

        public Builder stateStore(StateStore stateStore) {
            this.stateStore = stateStore;
            return this;
        }

        public <S extends WorkflowState> Builder register(Class<?> workflowClass, Object instance, S initialState) {
            workflows.add(new WorkflowRegistration<>(workflowClass, instance, initialState));
            return this;
        }

        public <S extends WorkflowState> Builder register(Class<?> workflowClass, S initialState) {
            return register(workflowClass, null, initialState);
        }

        public EngramClient build() {
            Objects.requireNonNull(codec, "codec is required");
            Objects.requireNonNull(eventLog, "eventLog is required");
            Objects.requireNonNull(stateStore, "stateStore is required");

            if (workflows.isEmpty()) {
                throw new IllegalStateException("No workflows registered");
            }

            Registry registry = new RegistryImpl(codec);
            Map<String, Coordinator<?>> coordinators = new LinkedHashMap<>();

            for (WorkflowRegistration<?> w : workflows) {
                var entry = createCoordinator(registry, w);
                coordinators.put(entry.getKey(), entry.getValue());
            }

            registry.freeze();

            return new EngramClient(codec, eventLog, stateStore, registry, Map.copyOf(coordinators));
        }

        private <S extends WorkflowState> Map.Entry<String, Coordinator<S>> createCoordinator(
                Registry registry, WorkflowRegistration<S> w) {

            WorkflowDefinition<S> definition = w.instance() != null
                    ? registry.register(w.workflowClass(), w.instance(), w.initialState())
                    : registry.register(w.workflowClass(), w.initialState());

            var coordinator = new Coordinator<>(
                    eventLog,
                    stateStore,
                    new AnnotatedDecider<>(definition, codec),
                    definition.stateType(),
                    w.initialState()
            );

            return Map.entry(definition.type(), coordinator);
        }
    }
}