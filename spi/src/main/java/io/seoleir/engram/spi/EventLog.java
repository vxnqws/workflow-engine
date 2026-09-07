package io.seoleir.engram.spi;

import io.seoleir.engram.core.event.HistoryEvent;

import java.util.List;

public interface EventLog {
    long append(String workflowId, List<HistoryEvent> events, long expectedSequence);
    List<HistoryEvent> read(String workflowId, long fromSequence);
    long readLastSequence(String workflowId);
    void truncate(String workflowId, long beforeSequence);
}
