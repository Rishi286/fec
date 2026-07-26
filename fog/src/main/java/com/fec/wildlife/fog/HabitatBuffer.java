package com.fec.wildlife.fog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;


public class HabitatBuffer {

    private final AtomicReference<Map<FieldKey, List<Reading>>> state = new AtomicReference<>(Map.of());
    private final Map<String, String> units = new ConcurrentHashMap<>();

    public void ingest(String sensorType, String siteId, String unit, List<Reading> readings) {
        FieldKey key = new FieldKey(sensorType, siteId);
        state.updateAndGet(currentSnapshot -> {
            Map<FieldKey, List<Reading>> next = new HashMap<>(currentSnapshot);
            List<Reading> merged = new ArrayList<>(next.getOrDefault(key, List.of()));
            merged.addAll(readings);
            next.put(key, List.copyOf(merged));
            return Map.copyOf(next);
        });
        if (unit != null && !unit.isEmpty()) units.put(sensorType, unit);
    }

    
    public Map<FieldKey, List<Reading>> drainAll() {
        return state.getAndSet(Map.of());
    }

    public String unitFor(String sensorType) {
        return units.getOrDefault(sensorType, "");
    }
}
