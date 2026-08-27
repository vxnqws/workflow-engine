package io.seoleir.engram.spi.exception;

public class SequenceConflictException extends RuntimeException {

    private final String message;

    public SequenceConflictException(String message) {
        super(message);
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
