package io.seoleir.engram.examples.order;

import io.seoleir.engram.backend.memory.InMemoryEventLog;
import io.seoleir.engram.backend.memory.InMemoryStateStore;
import io.seoleir.engram.client.EngramClient;
import io.seoleir.engram.codeccbor.CborCodec;
import io.seoleir.engram.core.codec.StateCodec;
import io.seoleir.engram.examples.order.model.EventTypes;
import io.seoleir.engram.examples.order.model.State;
import io.seoleir.engram.examples.order.payload.ChargeResult;
import io.seoleir.engram.examples.order.payload.OrderPlaced;
import io.seoleir.engram.examples.order.payload.ReserveResult;
import io.seoleir.engram.examples.order.workflow.OrderWorkflow;
import io.seoleir.engram.spi.EventLog;
import io.seoleir.engram.spi.StateStore;

import java.util.List;

public class OrderDemo {

    private static final String WORKFLOW_ID = "o-42";
    private static final String WORKFLOW_TYPE = "OrderWorkflow";

    public static void main(String[] args) {
        StateCodec codec = new CborCodec();
        EventLog log = new InMemoryEventLog();
        StateStore store = new InMemoryStateStore(codec);

        EngramClient engram = EngramClient.builder()
                .codec(codec)
                .eventLog(log)
                .stateStore(store)
                .register(OrderWorkflow.class, new OrderWorkflow(), State.initial())
                .build();

        System.out.println("=== Live execution ===");

        run(engram, store, EventTypes.ORDER_PLACED, new OrderPlaced("order-1", "cust-7"));
        run(engram, store, EventTypes.RESERVE_COMPLETED, new ReserveResult("r-88"));
        run(engram, store, EventTypes.CHARGE_COMPLETED, new ChargeResult("p-7", 149_900L));

        State live = (State) store.load(WORKFLOW_ID).state();

        System.out.println("\n=== Unknown event is ignored ===");
        var before = store.load(WORKFLOW_ID).state();
        run(engram, store, "SomeRemovedEventType", new byte[0]);
        var after = store.load(WORKFLOW_ID).state();
        System.out.println("State unchanged: " + before.equals(after));

        System.out.println("\n=== Crash: state lost, log intact ===");

        StateStore freshStore = new InMemoryStateStore(codec);
        EngramClient recoveredEngram = EngramClient.builder()
                .codec(codec)
                .eventLog(log)
                .stateStore(freshStore)
                .register(OrderWorkflow.class, new OrderWorkflow(), State.initial())
                .build();

        run(recoveredEngram, store, "Noop", new byte[0]);

        State restored = (State) freshStore.load(WORKFLOW_ID).state();

        System.out.println("Live:      " + live);
        System.out.println("Recovered: " + restored);
        System.out.println("Match:     " + live.equals(restored));

        System.out.println("\n=== Builder validation ===");
        try {
            EngramClient.builder().codec(codec).eventLog(log).stateStore(store).build();
        } catch (IllegalStateException e) {
            System.out.println("Caught as expected: " + e.getMessage());
        }
    }

    private static void run(EngramClient engramClient, StateStore store, String eventType, Object payload) {
        List<String> commands = engramClient.handle(WORKFLOW_TYPE, WORKFLOW_ID, eventType, payload);
        var vs = store.load(WORKFLOW_ID);
        System.out.printf("seq=%d version=%d state=%s commands=%s%n",
                vs.lastSequence(), vs.version(), vs.state(), commands);
    }
}
