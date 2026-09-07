package io.seoleir.engram.spi;

import io.seoleir.engram.api.state.WorkflowState;
import io.seoleir.engram.spi.state.VersionedState;

public interface StateStore {
    VersionedState load(String workflowId);
    long save(String workflowId, WorkflowState state, long lastSequence, long expectedVersion);
}
