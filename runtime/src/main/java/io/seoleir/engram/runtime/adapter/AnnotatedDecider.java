package io.seoleir.engram.runtime.adapter;

import io.seoleir.engram.api.state.Decision;
import io.seoleir.engram.api.state.WorkflowState;
import io.seoleir.engram.core.codec.StateCodec;
import io.seoleir.engram.core.decide.Decider;
import io.seoleir.engram.core.event.HistoryEvent;
import io.seoleir.engram.runtime.exception.DeciderInvocationException;
import io.seoleir.engram.runtime.registry.WorkflowDefinition;

import java.util.Objects;
import java.util.logging.Logger;


public final class AnnotatedDecider<S extends WorkflowState> implements Decider<S> {

    private static final Logger log = Logger.getLogger(AnnotatedDecider.class.getName());

    private final WorkflowDefinition<S> definition;
    private final StateCodec codec;

    public AnnotatedDecider(WorkflowDefinition<S> definition, StateCodec codec) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Decision<S> decide(S state, HistoryEvent event) {
        var handler = definition.handlerFor(event.type());

        if (handler == null) {
            log.fine(() -> "No handler for event '" + event.type()
                    + "' in workflow " + definition.type() + "; state unchanged");
            return Decision.stateOnly(state);
        }

        byte[] payload = event.payload();
        Object decoded = codec.decode(payload, handler.payloadType());

        try {
            return (Decision<S>) handler.methodHandle().invoke(definition.target(), state, decoded);
        } catch (RuntimeException | Error e) {
            throw e;                                    // пробрасываем как есть
        } catch (Throwable t) {
            throw new DeciderInvocationException(
                    "Handler for event '" + event.type() + "' in workflow "
                            + definition.type() + " threw a checked exception", t);
        }
    }
}
