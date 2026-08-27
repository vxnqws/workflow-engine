package io.seoleir.engram.core.decide;

import io.seoleir.engram.core.event.HistoryEvent;
import io.seoleir.engram.core.state.WorkflowState;

import java.util.List;

public class Replayer<S extends WorkflowState> {
    private final Decider<S> decider;

    public Replayer(Decider<S> decider) {
        this.decider = decider;
    }

    public S replay(S initial, List<HistoryEvent> events) {
        S state = initial;
        long expected = -1;
        for (HistoryEvent e : events) {
            if (expected >= 0 && e.sequence() != expected) {
                throw new IllegalStateException("Разрыв в истории: ожидался " + expected + ", получен " + e.sequence());
            }
            state = decider.decide(state, e).newState();
            expected = e.sequence() + 1;
        }
        return state;
    }
}
