package com.fec.wildlife.fog;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoublePredicate;
import java.util.function.ToDoubleFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rules are parsed once at class-init from human-readable DSL strings ("sensor_type avg&gt;75 -&gt; alert_key") via SPEC_PATTERN into CompiledRule instances holding already-bound extractor/test closures, so every evaluate() call is pure closure invocation with no field-name or operator branching left at runtime. */
public class HabitatAlerts {

    private static final Pattern SPEC_PATTERN =
        Pattern.compile("(\\w+)\\s+(avg|min|max)\\s*(>|<)\\s*(-?[0-9.]+)\\s*->\\s*(\\w+)");

    public static final List<CompiledRule> CATALOG = List.of(
        compile("acoustic_poaching_risk_db avg>75 -> poaching_risk_detected"),
        compile("waterhole_level_cm avg<20 -> drought_stress_risk"),
        compile("motion_detection_count max>30 -> unusual_activity_surge"),
        compile("soil_moisture_pct avg<10 -> habitat_dryness_risk")
        // ambient_temp_c intentionally has no rule spec here: one of the 5
        // required sensor types, shown on the dashboard, but never alerting.
    );

    static ToDoubleFunction<WindowAggregate> extractorFor(String field) {
        return switch (field) {
            case "avg" -> WindowAggregate::avg;
            case "min" -> WindowAggregate::min;
            case "max" -> WindowAggregate::max;
            default -> throw new IllegalStateException("unknown field: " + field);
        };
    }

    static CompiledRule compile(String spec) {
        Matcher m = SPEC_PATTERN.matcher(spec.trim());
        if (!m.matches()) throw new IllegalStateException("malformed rule spec: " + spec);
        String sensorType = m.group(1);
        String field = m.group(2);
        String op = m.group(3);
        double limit = Double.parseDouble(m.group(4));
        String key = m.group(5);

        ToDoubleFunction<WindowAggregate> extractor = extractorFor(field);
        DoublePredicate test = ">".equals(op) ? (actual -> actual > limit) : (actual -> actual < limit);
        return new CompiledRule(sensorType, field, op, limit, key, extractor, test);
    }

    public static List<String> evaluate(String sensorType, WindowAggregate window) {
        List<String> fired = new ArrayList<>();
        for (CompiledRule rule : CATALOG) {
            if (rule.sensorType().equals(sensorType) && rule.firesOn(window)) {
                fired.add(rule.key());
            }
        }
        return fired;
    }
}
