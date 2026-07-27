package com.fec.wildlife.fog;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

/** A class-specific StdSerializer registered via SimpleModule on a dedicated ObjectMapper. */
public class AggregateSerializer extends StdSerializer<AggregatePayload> {

    public AggregateSerializer() {
        super(AggregatePayload.class);
    }

    public static ObjectMapper newMapper() {
        ObjectMapper mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule("HabitatAggregateModule");
        module.addSerializer(AggregatePayload.class, new AggregateSerializer());
        mapper.registerModule(module);
        return mapper;
    }

    @Override
    public void serialize(AggregatePayload payload, JsonGenerator gen, SerializerProvider provider) throws IOException {
        WindowAggregate w = payload.window();
        gen.writeStartObject();
        gen.writeStringField("sensor_type", w.sensorType());
        gen.writeStringField("site_id", w.siteId());
        gen.writeStringField("unit", w.unit());
        gen.writeStringField("window_start", w.windowStart());
        gen.writeStringField("window_end", w.windowEnd());
        gen.writeNumberField("count", w.count());
        gen.writeNumberField("min", w.min());
        gen.writeNumberField("max", w.max());
        gen.writeNumberField("avg", w.avg());
        gen.writeNumberField("latest", w.latest());
        gen.writeArrayFieldStart("alerts");
        for (String alert : payload.alerts()) gen.writeString(alert);
        gen.writeEndArray();
        gen.writeEndObject();
    }
}
