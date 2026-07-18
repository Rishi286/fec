package com.fec.wildlife.fog;

import java.util.function.DoublePredicate;
import java.util.function.ToDoubleFunction;

/**
 * One threshold rule, already fully resolved. `field`/`op`/`limit`/`key` are
 * kept as plain data purely so GET /thresholds can render them verbatim, but
 * `extractor` and `test` are bound closures produced ONCE by
 * HabitatAlerts.compile() at class-init time from a human-readable DSL
 * string -- HabitatAlerts.evaluate() never branches on `field` or `op`
 * again, it only calls firesOn().
 */
public record CompiledRule(String sensorType, String field, String op, double limit, String key,
                            ToDoubleFunction<WindowAggregate> extractor, DoublePredicate test) {

    boolean firesOn(WindowAggregate window) {
        return test.test(extractor.applyAsDouble(window));
    }
}
