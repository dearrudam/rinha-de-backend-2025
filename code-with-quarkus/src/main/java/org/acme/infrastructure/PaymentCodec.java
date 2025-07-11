package org.acme.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.codecs.Codec;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.domain.Payment;

import java.io.IOException;
import java.lang.reflect.Type;

@ApplicationScoped
public class PaymentCodec implements Codec {

    private final ObjectMapper objectMapper;

    PaymentCodec(ObjectMapper objectMapper){
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean canHandle(Type clazz) {
        return clazz.equals(Payment.class);
    }

    @Override
    public byte[] encode(Object item) {
        if (item instanceof Payment) {
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
            return objectMapper.createParser(item).readValueAs(Payment.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
