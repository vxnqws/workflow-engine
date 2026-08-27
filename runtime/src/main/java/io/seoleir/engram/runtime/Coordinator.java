package io.seoleir.engram.runtime;

import io.seoleir.engram.core.decide.Decider;
import io.seoleir.engram.core.decide.Replayer;
import io.seoleir.engram.core.event.HistoryEvent;
import io.seoleir.engram.core.state.Decision;
import io.seoleir.engram.core.state.WorkflowState;
import io.seoleir.engram.spi.EventLog;
import io.seoleir.engram.spi.StateStore;
import io.seoleir.engram.spi.exception.ConcurrencyException;
import io.seoleir.engram.spi.state.VersionedState;

import java.util.List;
import java.util.Objects;

public final class Coordinator<S extends WorkflowState> {

    private static final int MAX_ATTEMPTS = 5;

    private final EventLog log;
    private final StateStore store;
    private final Decider<S> decider;
    private final Replayer<S> replayer;
    private final Class<S> stateType;
    private final S initialState;

    public Coordinator(EventLog log,
                       StateStore store,
                       Decider<S> decider,
                       Class<S> stateType,
                       S initialState) {
        this.log = Objects.requireNonNull(log);
        this.store = Objects.requireNonNull(store);
        this.decider = Objects.requireNonNull(decider);
        this.replayer = new Replayer<>(decider);
        this.stateType = Objects.requireNonNull(stateType);
        this.initialState = Objects.requireNonNull(initialState);
    }

    public List<String> handle(String workflowId, String eventType, byte[] payload) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return attemptTurn(workflowId, eventType, payload);
            } catch (ConcurrencyException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw new IllegalStateException("Не удалось обработать событие за " + MAX_ATTEMPTS + " попыток", e);
                }
            }
        }
        throw new AssertionError("Недостижимо");
    }

    private List<String> attemptTurn(String workflowId, String eventType, byte[] payload) {
        VersionedState loaded = store.load(workflowId);
        S state = loaded.isEmpty() ? initialState : stateType.cast(loaded.state());

        long lastSequence = log.readLastSequence(workflowId);
        if (loaded.lastSequence() < lastSequence) {
            state = replayer.replay(state, log.read(workflowId, loaded.lastSequence()));
        }

        HistoryEvent event = new HistoryEvent(workflowId, lastSequence + 1, eventType, payload);
        Decision<S> decision = decider.decide(state, event);

        log.append(workflowId, List.of(event), lastSequence);
        store.save(workflowId, decision.newState(), lastSequence + 1, loaded.version());

        return decision.commands();
    }
}

