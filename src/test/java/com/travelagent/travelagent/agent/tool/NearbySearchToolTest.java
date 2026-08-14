package com.travelagent.travelagent.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.travelagent.travelagent.agent.dto.AgentUserLocation;
import com.travelagent.travelagent.agent.dto.NearbySearchResult;
import com.travelagent.travelagent.agent.service.CurrentUserLocationContext;
import com.travelagent.travelagent.agent.service.NearbyPoiSearchService;
import com.travelagent.travelagent.agent.service.NearbySearchContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NearbySearchToolTest {

    @AfterEach
    void clearContexts() {
        CurrentUserLocationContext.clear();
        NearbySearchContext.clear();
    }

    @Test
    void returnsUserFacingMessageWhenNoNearbyPlaceExists() {
        NearbyPoiSearchService searchService = mock(NearbyPoiSearchService.class);
        when(searchService.search(30.0, 120.0, "景点", 20_000, List.of()))
                .thenReturn(new NearbySearchResult(List.of(), false, List.of(), "景点", 30.0, 120.0, 20_000));
        CurrentUserLocationContext.set(new AgentUserLocation(30.0, 120.0));

        JSONObject response = JSON.parseObject(new NearbySearchTool(searchService).searchNearbyPois("景点"));

        assertThat(response.getString("summary")).isEqualTo("当前位置附近暂未找到合适的推荐地点");
        assertThat(response.toJSONString()).doesNotContain("POI");
    }
}
