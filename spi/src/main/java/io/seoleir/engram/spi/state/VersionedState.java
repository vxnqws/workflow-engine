package io.seoleir.engram.spi.state;

import io.seoleir.engram.core.state.WorkflowState;

public record VersionedState(WorkflowState state, long version, long lastSequence) {

    public static VersionedState empty() {
        return new VersionedState(null, 0L, 0L);
    }

    public boolean isEmpty() {
        return state == null;
    }
}