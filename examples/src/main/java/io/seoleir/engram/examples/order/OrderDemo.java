package io.seoleir.engram.examples.order;

import io.seoleir.engram.backendmemory.InMemoryEventLog;
import io.seoleir.engram.backendmemory.InMemoryStateStore;
import io.seoleir.engram.codeccbor.CborCodec;
import io.seoleir.engram.core.codec.StateCodec;
import io.seoleir.engram.examples.order.decider.OrderDecider;
import io.seoleir.engram.examples.order.model.EventTypes;
import io.seoleir.engram.examples.order.model.State;
import io.seoleir.engram.examples.order.payload.ChargeResult;
import io.seoleir.engram.examples.order.payload.OrderPlaced;
import io.seoleir.engram.examples.order.payload.ReserveResult;
import io.seoleir.engram.runtime.Coordinator;
import io.seoleir.engram.spi.EventLog;
import io.seoleir.engram.spi.StateStore;

public class OrderDemo {
    public static void main(String[] args) {
        StateCodec codec = new CborCodec();
        EventLog log = new InMemoryEventLog();
        StateStore store = new InMemoryStateStore(codec);

        Coordinator<State> coordinator = new Coordinator<>(log, store, new OrderDecider(codec), State.class, State.initial());

        String workflowId = "o-42";

        System.out.println("=== Живое исполнение ===");

        var commands = coordinator.handle(workflowId, EventTypes.ORDER_PLACED,
                codec.encode(new OrderPlaced("order-1", "cust-7")));
        print(store, workflowId, commands);

        commands = coordinator.handle(workflowId, EventTypes.RESERVE_COMPLETED,
                codec.encode(new ReserveResult("r-88")));
        print(store, workflowId, commands);

        commands = coordinator.handle(workflowId, EventTypes.CHARGE_COMPLETED,
                codec.encode(new ChargeResult("p-7", 149_900L)));
        print(store, workflowId, commands);

        var live = store.load(workflowId).state();

        System.out.println("\n=== Краш: состояние потеряно, лог цел ===");

        StateStore freshStore = new InMemoryStateStore(codec);
        var recovered = new Coordinator<>(
                log, freshStore, new OrderDecider(codec), State.class, State.initial());

        recovered.handle(workflowId, "Noop", new byte[0]);

        var restored = freshStore.load(workflowId).state();

        System.out.println("Живое:          " + live);
        System.out.println("Восстановленное: " + restored);
        System.out.println("Совпало: " + statesEqual(live, restored));
    }

    private static void print(StateStore store, String workflowId, java.util.List<String> commands) {
        var vs = store.load(workflowId);
        System.out.printf("seq=%d version=%d state=%s commands=%s%n",
                vs.lastSequence(), vs.version(), vs.state(), commands);
    }

    private static boolean statesEqual(Object live, Object restored) {
        if (!(live instanceof State a) || !(restored instanceof State b)) return false;
        return a.status() == b.status()
                && java.util.Objects.equals(a.reservationId(), b.reservationId())
                && java.util.Objects.equals(a.paymentId(), b.paymentId());
    }

}
