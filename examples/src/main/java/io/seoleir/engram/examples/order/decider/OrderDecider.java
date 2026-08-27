package io.seoleir.engram.examples.order.decider;

import io.seoleir.engram.core.codec.StateCodec;
import io.seoleir.engram.core.decide.Decider;
import io.seoleir.engram.core.event.HistoryEvent;
import io.seoleir.engram.core.state.Decision;
import io.seoleir.engram.examples.order.model.EventTypes;
import io.seoleir.engram.examples.order.model.State;
import io.seoleir.engram.examples.order.model.Status;
import io.seoleir.engram.examples.order.payload.ChargeResult;
import io.seoleir.engram.examples.order.payload.ReserveResult;

import java.util.List;
import java.util.Objects;

public final class OrderDecider implements Decider<State> {

    private final StateCodec codec;

    public OrderDecider(StateCodec codec) {
        this.codec = Objects.requireNonNull(codec);
    }

    @Override
    public Decision<State> decide(State state, HistoryEvent event) {
        return switch (event.type()) {
            case EventTypes.ORDER_PLACED -> new Decision<>(
                    state.withStatus(Status.RESERVING),
                    List.of("reserve"));
            case EventTypes.RESERVE_COMPLETED -> {
                var payload = codec.decode(event.payload(), ReserveResult.class);
                yield new Decision<>(
                        state.withStatus(Status.CHARGING)
                                .withReservation(payload.reservationId()),
                        List.of("charge"));
            }
            case EventTypes.CHARGE_COMPLETED -> {
                var payload = codec.decode(event.payload(), ChargeResult.class);
                yield new Decision<>(
                        state.withStatus(Status.COMPLETED)
                                .withPayment(payload.paymentId()),
                        List.of());
            }

            default -> Decision.stateOnly(state);
        };
    }
}
