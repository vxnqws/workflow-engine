package io.seoleir.engram.examples.order.model;

import io.seoleir.engram.core.state.WorkflowState;

public record State(
        Status status,
        String reservationId,
        String paymentId
) implements WorkflowState {

    public static State initial() {
        return new State(Status.NEW, null, null);
    }

    public State withStatus(Status status) {
        return new State(status, reservationId, paymentId);
    }

    public State withReservation(String reservationId) {
        return new State(status, reservationId, paymentId);
    }

    public State withPayment(String paymentId) {
        return new State(status, reservationId, paymentId);
    }
}
