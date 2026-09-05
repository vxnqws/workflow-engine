package io.seoleir.engram.core.internal.codec;

public interface StateCodec {
    byte[] encode(Object value);
    <T> T decode(byte[] bytes, Class<T> type);
}
