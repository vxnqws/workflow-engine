package io.seoleir.engram.codeccbor;

import io.seoleir.engram.codeccbor.exception.SerializationException;
import io.seoleir.engram.core.internal.codec.StateCodec;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.dataformat.cbor.CBORMapper;

public final class CborCodec implements StateCodec {

    private final CBORMapper cborMapper;

    public CborCodec() {
        this.cborMapper = CBORMapper.builder()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    @Override
    public byte[] encode(Object value) {
        try {
            if (value == null) return new byte[0];

            return cborMapper.writeValueAsBytes(value);
        } catch (JacksonException e) {
            throw new SerializationException("Could not serialize " + value.getClass(), e);
        }
    }

    @Override
    public <T> T decode(byte[] bytes, Class<T> type) {
        try {
            if (bytes == null || bytes.length == 0) return null;

            return cborMapper.readValue(bytes, type);
        } catch (JacksonException e) {
            throw new SerializationException("Could not deserialize to " + type, e);
        }
    }
}
