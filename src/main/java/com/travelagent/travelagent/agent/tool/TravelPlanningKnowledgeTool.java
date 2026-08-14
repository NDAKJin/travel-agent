package com.travelagent.travelagent.agent.tool;

import com.alibaba.fastjson2.JSON;
import com.travelagent.travelagent.agent.service.ScenicSpotKnowledgeGraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TravelPlanningKnowledgeTool {

    private final ScenicSpotKnowledgeGraphService knowledgeGraphService;

    @Tool(name = "search_travel_knowledge", description = "检索后台维护的景点知识库。当用户要求旅游规划、行程安排、景点推荐或路线建议时，先调用此工具；query 传入用户目的地、偏好和需求的简洁中文摘要。")
    public String searchTravelKnowledge(String query) {
        return JSON.toJSONString(knowledgeGraphService.search(query, 8));
    }

    @Tool(name = "find_scenic_shortest_path", description = "查询两个景点之间的基础最短路径。fromId 和 toId 必须使用 search_travel_knowledge 返回的景点 ID。")
    public String findScenicShortestPath(String fromId, String toId) {
        return JSON.toJSONString(knowledgeGraphService.shortestPath(fromId, toId));
    }
}
