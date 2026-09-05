package io.seoleir.engram.core.internal.event;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

public record HistoryEvent (
        String workflowId,
        long sequence,
        String type,
        byte[] payload
) {
    @Override
    public byte[] payload() {
        return payload.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        HistoryEvent that = (HistoryEvent) o;
        return sequence == that.sequence
                && Objects.equals(type, that.type)
                && Arrays.equals(payload, that.payload)
                && Objects.equals(workflowId, that.workflowId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workflowId, sequence, type, Arrays.hashCode(payload));
    }
}
