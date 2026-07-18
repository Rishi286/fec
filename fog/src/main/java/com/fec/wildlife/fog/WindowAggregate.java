package com.fec.wildlife.fog;

import java.util.List;

/** Aggregate statistics computed over one flush window's buffered readings for one reserve/sensor pair. */
public record WindowAggregate(String sensorType, String siteId, String unit, String windowStart, String windowEnd,
                               int count, double min, double max, double avg, double latest) {

    public static WindowAggregate of(String sensorType, String siteId, String unit, List<Reading> readings,
                                      String windowStart, String windowEnd) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double sum = 0;
        for (Reading r : readings) {
            min = Math.min(min, r.value());
            max = Math.max(max, r.value());
            sum += r.value();
        }
        double avg = Math.round((sum / readings.size()) * 1000.0) / 1000.0;
        // Readings are appended to the buffered list in arrival order and
        // never reordered, so the last element is the most recently sampled
        // reading -- "latest" is last-in-order, not the max value or the max
        // timestamp.
        double latest = readings.get(readings.size() - 1).value();
        return new WindowAggregate(sensorType, siteId, unit, windowStart, windowEnd, readings.size(), min, max, avg, latest);
    }
}
