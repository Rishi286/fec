package com.fec.wildlife.fog;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WindowAggregateTest {

    @Test
    void computesRealMinMaxAvgAndLatest() {
        List<Reading> readings = List.of(
            new Reading("t0", 40.0),
            new Reading("t1", 50.0),
            new Reading("t2", 45.0)
        );
        WindowAggregate w = WindowAggregate.of("acoustic_poaching_risk_db", "reserve-a", "dB", readings, "s", "e");

        assertEquals(3, w.count());
        assertEquals(40.0, w.min());
        assertEquals(50.0, w.max());
        assertEquals(45.0, w.avg());
        assertEquals(45.0, w.latest());
    }

    @Test
    void avgIsRoundedToThreeDecimalPlaces() {
        List<Reading> readings = List.of(new Reading("t0", 1.0), new Reading("t1", 2.0), new Reading("t2", 2.0));
        WindowAggregate w = WindowAggregate.of("soil_moisture_pct", "reserve-b", "%", readings, "s", "e");
        assertEquals(1.667, w.avg());
    }

    @Test
    void latestIsTheLastReadingInArrivalOrderNotTheMax() {
        List<Reading> readings = List.of(new Reading("t0", 90.0), new Reading("t1", 40.0));
        WindowAggregate w = WindowAggregate.of("waterhole_level_cm", "reserve-a", "cm", readings, "s", "e");
        assertEquals(40.0, w.latest());
        assertEquals(90.0, w.max());
    }
}
