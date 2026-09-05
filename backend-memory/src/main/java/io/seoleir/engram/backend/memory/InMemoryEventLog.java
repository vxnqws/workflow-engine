package io.seoleir.engram.backend.memory;

import io.seoleir.engram.core.internal.event.HistoryEvent;
import io.seoleir.engram.spi.EventLog;
import io.seoleir.engram.spi.exception.SequenceConflictException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryEventLog implements EventLog {

    private final Map<String, List<HistoryEvent>> eventLog = new ConcurrentHashMap<>();

    @Override
    public long append(String workflowId, List<HistoryEvent> events, long expectedSequence) {
        AtomicLong result = new AtomicLong();

        eventLog.compute(workflowId, (key, current) -> {
            List<HistoryEvent> list = current == null ? new CopyOnWriteArrayList<>() : current;
            long actualSequence = list.isEmpty() ? 0L : list.getLast().sequence();

            if (actualSequence != expectedSequence) {
                String message = "Конфликт для %s: ожидался last=%d, в логе last=%d"
                        .formatted(workflowId, expectedSequence, actualSequence);
                throw new SequenceConflictException(message);
            }

            long nextSequence = actualSequence;
            for (HistoryEvent e : events) {
                if (e.sequence() != ++nextSequence) {
                    throw new IllegalArgumentException("Разрыв: ожидался sequence " + nextSequence + ", получен " + e.sequence());
                }
            }

            list.addAll(events);
            result.set(nextSequence);
            return list;
        });

        return result.get();
    }

    @Override
    public List<HistoryEvent> read(String workflowId, long fromSequence) {
        return eventLog.getOrDefault(workflowId, List.of())
                .stream()
                .filter(event -> event.sequence() >= fromSequence)
                .toList();
    }

    @Override
    public long readLastSequence(String workflowId) {
        List<HistoryEvent> events = eventLog.get(workflowId);
        return (events == null || events.isEmpty()) ? 0L : events.getLast().sequence();
    }

    @Override
    public void truncate(String workflowId, long beforeSequence) {
        eventLog.computeIfPresent(workflowId, (key, events) -> events.stream()
                .filter(event -> event.sequence() > beforeSequence)
                .toList());
    }
}
