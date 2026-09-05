package io.seoleir.engram.backend.memory;

import io.seoleir.engram.core.internal.codec.StateCodec;
import io.seoleir.engram.core.api.state.WorkflowState;
import io.seoleir.engram.spi.StateStore;
import io.seoleir.engram.spi.exception.OptimisticLockException;
import io.seoleir.engram.spi.state.VersionedState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryStateStore implements StateStore {
    private final Map<String, Entry> states = new ConcurrentHashMap<>();
    private final StateCodec codec;

    public InMemoryStateStore(StateCodec codec) {
        this.codec = codec;
    }

    @Override
    public VersionedState load(String workflowId) {
        var e = states.get(workflowId);
        if (e == null) return VersionedState.empty();
        var state = (WorkflowState) codec.decode(e.bytes(), e.type());

        return new VersionedState(state, e.version(), e.lastSequence());
    }

    @Override
    public long save(String workflowId, WorkflowState state, long lastSequence, long expectedVersion) {
        byte[] encoded = codec.encode(state);
        AtomicLong result = new AtomicLong();

        states.compute(workflowId, (id, current) -> {
            long actual = current == null ? 0L : current.version();
            if (actual != expectedVersion) {
                throw new OptimisticLockException(
                        "Конфликт версии для " + workflowId
                                + ": ожидалась " + expectedVersion + ", в сторе " + actual);
            }
            long next = actual + 1;
            result.set(next);
            return new Entry(encoded, state.getClass(), next, lastSequence);
        });

        return result.get();
    }

    private record Entry(byte[] bytes, Class<?> type, long version, long lastSequence) {}
}
