package io.seoleir.engram.core.internal.decide;

import io.seoleir.engram.core.internal.event.HistoryEvent;
import io.seoleir.engram.core.api.state.Decision;
import io.seoleir.engram.core.api.state.WorkflowState;

@FunctionalInterface
public interface Decider <S extends WorkflowState> {
    Decision<S> decide(S state, HistoryEvent event);
}
