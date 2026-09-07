package io.seoleir.engram.api.state;

import java.util.List;

public record Decision<S extends WorkflowState> (
        S newState,
        List<String> commands
) {
    public static <S extends WorkflowState> Decision<S> stateOnly(S state) {
        return new Decision<>(state, null);
    }
}
