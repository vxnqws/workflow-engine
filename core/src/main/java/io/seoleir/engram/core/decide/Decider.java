package io.seoleir.engram.core.decide;

import io.seoleir.engram.core.event.HistoryEvent;
import io.seoleir.engram.core.state.Decision;
import io.seoleir.engram.core.state.WorkflowState;

@FunctionalInterface
public interface Decider <S extends WorkflowState> {
    Decision<S> decide(S state, HistoryEvent event);
}
