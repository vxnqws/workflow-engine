package io.seoleir.engram.runtime.registry;

import io.seoleir.engram.core.api.state.WorkflowState;

import java.lang.invoke.MethodHandle;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class WorkflowDefinition<S extends WorkflowState> {

    private final String type;
    private final Class<S> stateType;
    private final S initialState;
    private final Object target;
    private final Map<String, Handler> handlers;

    WorkflowDefinition(String type,
                       Class<S> stateType,
                       S initialState,
                       Object target,
                       Map<String, Handler> handlers) {
        this.type = type;
        this.stateType = stateType;
        this.initialState = initialState;
        this.target = target;
        this.handlers = Map.copyOf(handlers);
    }

    public String type() {
        return type;
    }

    public Class<S> stateType() {
        return stateType;
    }

    public S initialState() {
        return initialState;
    }

    public Object target() {
        return target;
    }

    public Set<String> eventTypes() {
        return handlers.keySet();
    }

    public Handler handlerFor(String eventType) {
        return handlers.get(eventType);
    }

    @Override
    public String toString() {
        return "WorkflowDefinition[" + type + ", state=" + stateType.getSimpleName() + ", handlers=" + handlers.size() + "]";
    }


    public record Handler(MethodHandle methodHandle, Class<?> payloadType) {
        public Handler {
            Objects.requireNonNull(methodHandle);
            Objects.requireNonNull(payloadType);
        }
    }


}
