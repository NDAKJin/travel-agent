package com.travelagent.travelagent.infrastructure.messaging.rag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Parses the JSON FlatMessage emitted by Canal's built-in Kafka connector. */
public final class CanalFlatMessageParser {
    private CanalFlatMessageParser() {
    }

    public static List<JSONObject> insertedRows(String raw) {
        JSONObject message = JSON.parseObject(raw);
        if (message == null || !"INSERT".equalsIgnoreCase(message.getString("type"))) {
            return List.of();
        }
        JSONArray data = message.getJSONArray("data");
        if (data == null || data.isEmpty()) return List.of();
        List<JSONObject> rows = new ArrayList<>(data.size());
        for (Object item : data) {
            if (item instanceof JSONObject row) rows.add(row);
            else if (item != null) rows.add(JSON.parseObject(JSON.toJSONString(item)));
        }
        return rows;
    }
}
