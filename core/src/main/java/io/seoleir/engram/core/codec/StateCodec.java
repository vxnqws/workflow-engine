package io.seoleir.engram.core.codec;

public interface StateCodec {
    byte[] encode(Object value);
    <T> T decode(byte[] bytes, Class<T> type);
}
