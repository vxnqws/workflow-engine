package io.seoleir.engram.examples.order.workflow;

import io.seoleir.engram.core.api.annotation.OnEvent;
import io.seoleir.engram.core.api.annotation.WorkflowInterface;
import io.seoleir.engram.core.api.state.Decision;
import io.seoleir.engram.examples.order.model.EventTypes;
import io.seoleir.engram.examples.order.model.State;
import io.seoleir.engram.examples.order.model.Status;
import io.seoleir.engram.examples.order.payload.ChargeResult;
import io.seoleir.engram.examples.order.payload.OrderPlaced;
import io.seoleir.engram.examples.order.payload.ReserveResult;

import java.util.List;
import java.util.logging.Logger;

@WorkflowInterface(type = "OrderWorkflow")
public class OrderWorkflow {

    private static final Logger log = Logger.getLogger(OrderWorkflow.class.getName());

    @OnEvent(EventTypes.ORDER_PLACED)
    public Decision<State> onOrderPlaced(State state, OrderPlaced payload) {
        log.info("Order placed with status: "+ state.status() + " and payload: " + payload);

        return new Decision<>(
                state.withStatus(Status.RESERVING),
                List.of("reserve"));
    }

    @OnEvent(EventTypes.RESERVE_COMPLETED)
    public Decision<State> onReserveCompleted(State state, ReserveResult payload) {
        log.info("Reserve completed with status: "+ state.status() + " and payload: " + payload);

        return new Decision<>(
                state.withStatus(Status.CHARGING).withReservation(payload.reservationId()),
                List.of("charge"));
    }

    @OnEvent(EventTypes.CHARGE_COMPLETED)
    public Decision<State> onChargeCompleted(State state, ChargeResult payload) {
        log.info("Charge completed with status: "+ state.status() + " and payload: " + payload);

        return new Decision<>(
                state.withStatus(Status.COMPLETED).withPayment(payload.paymentId()),
                List.of());
    }

}
