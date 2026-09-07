package io.seoleir.engram.core.decide;

import io.seoleir.engram.api.state.Decision;
import io.seoleir.engram.api.state.WorkflowState;
import io.seoleir.engram.core.event.HistoryEvent;


@FunctionalInterface
public interface Decider <S extends WorkflowState> {
    Decision<S> decide(S state, HistoryEvent event);
}
