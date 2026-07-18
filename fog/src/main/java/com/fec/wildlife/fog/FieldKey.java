package com.fec.wildlife.fog;

/** Identifies one buffered group inside HabitatBuffer: a (sensor_type, site_id) pair, e.g. (waterhole_level_cm, reserve-a). */
public record FieldKey(String sensorType, String siteId) {}
