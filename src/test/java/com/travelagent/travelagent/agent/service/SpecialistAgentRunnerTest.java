package com.travelagent.travelagent.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

class SpecialistAgentRunnerTest {

    @Test
    void preservesTheStructuredSpecialistResult() {
        JSONObject result = JSON.parseObject(SpecialistAgentRunner.normalize("route", """
                {"status":"success","summary":"已找到路线","data":{"distanceMeters":3200},"warnings":[]}
                """));

        assertThat(result.getString("agent")).isEqualTo("route");
        assertThat(result.getJSONObject("data").getInteger("distanceMeters")).isEqualTo(3200);
    }

    @Test
    void wrapsUnstructuredSpecialistOutput() {
        JSONObject result = JSON.parseObject(SpecialistAgentRunner.normalize("poi", "附近有一家餐厅"));

        assertThat(result.getString("status")).isEqualTo("partial");
        assertThat(result.getJSONArray("warnings")).isNotEmpty();
    }
}
