package com.fec.wildlife.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure transform from one fog-dispatched SQS message body into a DynamoDB
 * item. Partition key is sensor_type alone, so without a compound sort key
 * reserve-a and reserve-b reporting the same sensor_type in the same flush
 * cycle would collide on the same item. window_end leads the sort key (not
 * site_id) so DynamoDB's native key ordering is already chronological for
 * the dashboard's "most recent windows" query; site_id is only appended to
 * disambiguate ties within one window.
 */
public class RecordMapper {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static Map<String, AttributeValue> toItem(String messageBody) throws Exception {
        JsonNode data = JSON.readTree(messageBody);
        String siteId = data.has("site_id") ? data.get("site_id").asText() : "reserve-a";
        String windowEnd = data.get("window_end").asText();
        String sortKey = windowEnd + "#" + siteId;

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("sensor_type", AttributeValue.fromS(data.get("sensor_type").asText()));
        item.put("sort_key", AttributeValue.fromS(sortKey));
        item.put("window_start", AttributeValue.fromS(data.get("window_start").asText()));
        item.put("window_end", AttributeValue.fromS(windowEnd));
        item.put("site_id", AttributeValue.fromS(siteId));
        item.put("unit", AttributeValue.fromS(data.has("unit") ? data.get("unit").asText() : ""));
        item.put("count", AttributeValue.fromN(String.valueOf(data.get("count").asInt())));
        item.put("min", AttributeValue.fromN(String.valueOf(data.get("min").asDouble())));
        item.put("max", AttributeValue.fromN(String.valueOf(data.get("max").asDouble())));
        item.put("avg", AttributeValue.fromN(String.valueOf(data.get("avg").asDouble())));
        item.put("latest", AttributeValue.fromN(String.valueOf(data.get("latest").asDouble())));

        List<AttributeValue> alerts = new ArrayList<>();
        if (data.has("alerts")) {
            for (JsonNode a : data.get("alerts")) alerts.add(AttributeValue.fromS(a.asText()));
        }
        item.put("alerts", AttributeValue.fromL(alerts));

        return item;
    }
}
