package org.acme.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.codecs.Codec;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.domain.Payment;
import org.acme.domain.RemotePaymentProcessorHealth;

import java.io.IOException;
import java.lang.reflect.Type;

@ApplicationScoped
public class RemotePaymentProcessorHealthCodec implements Codec {

    private final ObjectMapper objectMapper;

    public RemotePaymentProcessorHealthCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean canHandle(Type clazz) {
        return clazz.equals(RemotePaymentProcessorHealth.class);
    }

    @Override
    public byte[] encode(Object item) {
        if (item instanceof RemotePaymentProcessorHealth) {
            try {
                return objectMapper.writeValueAsBytes(item);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return new byte[0];
    }

    @Override
    public Object decode(byte[] item) {
        try {
            return objectMapper.createParser(item).readValueAs(RemotePaymentProcessorHealth.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
