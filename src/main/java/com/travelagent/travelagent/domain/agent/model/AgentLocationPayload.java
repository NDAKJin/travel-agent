package com.travelagent.travelagent.domain.agent.model;

import java.time.Instant;

/** 传递给 Agent 工作流的用户位置 JSON 载荷。 */
public record AgentLocationPayload(Double latitude, Double longitude,
                                   Double accuracy, String updatedAt) {
    /**
     * 根据位置坐标创建带更新时间的位置载荷。
     *
     * @param latitude 纬度
     * @param longitude 经度
     * @param accuracy 定位精度，单位为米
     * @return 位置 JSON 载荷
     */
    public static AgentLocationPayload from(Double latitude, Double longitude, Double accuracy) {
        return new AgentLocationPayload(latitude, longitude, accuracy, Instant.now().toString());
    }
}
