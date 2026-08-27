package io.seoleir.engram.codeccbor;

import io.seoleir.engram.codeccbor.exception.SerializationException;
import io.seoleir.engram.core.codec.StateCodec;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.dataformat.cbor.CBORMapper;

public class CborCodec implements StateCodec {

    private final CBORMapper cborMapper;

    public CborCodec() {
        this.cborMapper = CBORMapper.builder()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .build();
    }

    @Override
    public byte[] encode(Object value) {
        try {
            return cborMapper.writeValueAsBytes(value);
        } catch (JacksonException e) {
            throw new SerializationException("Не удалось сериализовать " + value.getClass(), e);
        }
    }

    @Override
    public <T> T decode(byte[] bytes, Class<T> type) {
        try {
            return cborMapper.readValue(bytes, type);
        } catch (JacksonException e) {
            throw new SerializationException("Не удалось десериализовать в " + type, e);
        }
    }
}
