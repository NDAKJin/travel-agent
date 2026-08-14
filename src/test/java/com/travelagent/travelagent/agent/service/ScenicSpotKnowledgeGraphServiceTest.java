package com.travelagent.travelagent.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ScenicSpotKnowledgeGraphServiceTest {

    @Test
    void buildsEmbeddingTextFromNameAndDescription() {
        assertEquals("故宫\n北京\n明清皇家宫殿", ScenicSpotKnowledgeGraphService.knowledgeText("故宫", "北京", "明清皇家宫殿"));
    }

    @Test
    void usesCypherShortestPathOverNearbyRelationships() {
        assertEquals(true, ScenicSpotKnowledgeGraphService.shortestPathQuery().contains("shortestPath((from)-[:CONNECTED_TO*..10]-(to))"));
    }
}
