package com.fec.wildlife.dashboard;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** DynamoDB access: recent windows per sensor type, and the project-specific per-reserve grouping view. */
class ReserveRepository {

    static Object unwrap(AttributeValue av) {
        if (av.s() != null) return av.s();
        if (av.n() != null) return Double.parseDouble(av.n());
        if (av.hasL()) {
            List<Object> out = new ArrayList<>();
            for (AttributeValue element : av.l()) out.add(unwrap(element));
            return out;
        }
        return null;
    }

    static Map<String, Object> unwrapItem(Map<String, AttributeValue> item) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (var entry : item.entrySet()) out.put(entry.getKey(), unwrap(entry.getValue()));
        return out;
    }

    List<Map<String, Object>> recentWindows(DynamoDbClient client, String tableName, String sensorType, int limit) {
        QueryRequest request = QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression("sensor_type = :st")
            .expressionAttributeValues(Map.of(":st", AttributeValue.fromS(sensorType)))
            .scanIndexForward(false)
            .limit(limit)
            .build();

        List<Map<String, Object>> items = new ArrayList<>();
        for (var item : client.query(request).items()) items.add(unwrapItem(item));
        Collections.reverse(items);
        return items;
    }

    /** Per-reserve view whose primary structure is a "log" -- all fetched windows across every sensor type flattened into one window_end-descending ledger, not a per-metric card grid. */
    Map<String, Object> byReserve(DynamoDbClient client, String tableName, String[] sensorTypes,
                                  int historyPerType, int logEntriesPerReserve) {
        Map<String, Map<String, Object>> metricsBySite = new TreeMap<>();
        Map<String, List<Map<String, Object>>> logBySite = new TreeMap<>();

        for (String sensorType : sensorTypes) {
            List<Map<String, Object>> recent = recentWindows(client, tableName, sensorType, historyPerType);
            Map<String, List<Map<String, Object>>> bySite = new TreeMap<>();
            for (var item : recent) {
                bySite.computeIfAbsent((String) item.get("site_id"), s -> new ArrayList<>()).add(item);
            }
            for (var entry : bySite.entrySet()) {
                String siteId = entry.getKey();
                List<Map<String, Object>> history = entry.getValue();
                Map<String, Object> latest = history.get(history.size() - 1);

                Map<String, Object> metricEntry = new LinkedHashMap<>();
                metricEntry.put("unit", latest.get("unit"));
                metricEntry.put("latest", latest.get("latest"));
                metricEntry.put("min", latest.get("min"));
                metricEntry.put("max", latest.get("max"));
                metricEntry.put("avg", latest.get("avg"));
                metricEntry.put("count", latest.get("count"));
                metricEntry.put("window_end", latest.get("window_end"));
                metricEntry.put("alerts", latest.get("alerts"));
                metricsBySite.computeIfAbsent(siteId, s -> new LinkedHashMap<>()).put(sensorType, metricEntry);

                List<Map<String, Object>> log = logBySite.computeIfAbsent(siteId, s -> new ArrayList<>());
                for (Map<String, Object> window : history) {
                    Map<String, Object> logEntry = new LinkedHashMap<>();
                    logEntry.put("sensor_type", sensorType);
                    logEntry.put("window_end", window.get("window_end"));
                    logEntry.put("unit", window.get("unit"));
                    logEntry.put("avg", window.get("avg"));
                    logEntry.put("latest", window.get("latest"));
                    logEntry.put("alerts", window.get("alerts"));
                    log.add(logEntry);
                }
            }
        }

        List<Object> reserves = new ArrayList<>();
        for (String siteId : metricsBySite.keySet()) {
            List<Map<String, Object>> log = logBySite.getOrDefault(siteId, List.of());
            log.sort(Comparator.comparing((Map<String, Object> e) -> (String) e.get("window_end")).reversed());
            List<Map<String, Object>> trimmedLog = log.size() > logEntriesPerReserve
                ? new ArrayList<>(log.subList(0, logEntriesPerReserve))
                : log;

            Map<String, Object> reserve = new LinkedHashMap<>();
            reserve.put("site_id", siteId);
            reserve.put("metrics", metricsBySite.get(siteId));
            reserve.put("log", trimmedLog);
            reserves.add(reserve);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reserves", reserves);
        return result;
    }
}
