package org.acme.infrastructure;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import static java.util.Optional.ofNullable;

@ApplicationScoped
public class Iso8601InstantConverter implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper objectMapper) {
        objectMapper.registerModule(new InstantModule());
    }

    public static class InstantModule extends SimpleModule {
        public InstantModule() {
            super("InstantModule");
            addSerializer(Instant.class, new InstantSerializer());
            addDeserializer(Instant.class, new InstantDeserializer());
        }
    }

    public static class InstantSerializer extends StdSerializer<Instant> {
        public InstantSerializer() {
            super(Instant.class);
        }

        @Override
        public void serialize(Instant value, com.fasterxml.jackson.core.JsonGenerator gen, com.fasterxml.jackson.databind.SerializerProvider provider) throws java.io.IOException, JacksonException {
            gen.writeString(Iso8601InstantConverter.serialize(value));
        }
    }

    public static class InstantDeserializer extends StdDeserializer<Instant> implements BiFunction<ObjectMapper, Type, ObjectReader> {

        public InstantDeserializer() {
            super(Instant.class);
        }

        @Override
        public Instant deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
            return Iso8601InstantConverter.parse(jsonParser.readValueAs(String.class));
        }

        @Override
        public ObjectReader apply(ObjectMapper objectMapper, Type type) {
            return null;
        }
    }

    private static final List<DateTimeFormatter> SERIALIZERS = List.of(
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ISO_ZONED_DATE_TIME,
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ISO_DATE,
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ISO_LOCAL_TIME
    );

    public static String serialize(Instant instant) {
        return ofNullable(instant)
                .map(DateTimeFormatter.ISO_OFFSET_DATE_TIME::format)
                .orElse(null);
    }

    public static Instant parse(String input) {
        return ofNullable(input).flatMap(i -> SERIALIZERS.stream()
                .map(formatter -> {
                    try {
                        TemporalAccessor value = formatter.parseBest(i,
                                Instant::from,
                                LocalDateTime::from,
                                LocalDate::from,
                                LocalTime::from);
                        return switch (value) {
                            case Instant instant -> Optional.of(instant);
                            case LocalDateTime localDateTime ->
                                    Optional.of(localDateTime).map(l -> l.toInstant(ZoneOffset.UTC));
                            case LocalDate localDate ->
                                    Optional.of(localDate).map(l -> l.atStartOfDay().toInstant(ZoneOffset.UTC));
                            case LocalTime localTime ->
                                    Optional.of(localTime).map(l -> l.atDate(LocalDate.now()).toInstant(ZoneOffset.UTC));
                            default -> Optional.<Instant>empty();
                        };
                    } catch (Exception e) {
                        return Optional.<Instant>empty();
                    }
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst()).orElse(null);
    }
}

