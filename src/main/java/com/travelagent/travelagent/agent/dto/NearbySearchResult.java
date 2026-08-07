package com.travelagent.travelagent.agent.dto;

import java.util.List;

public record NearbySearchResult(List<NearbyPoi> pois, boolean hasMore, List<Object> searchAfter,
                                 String keyword, double latitude, double longitude, int radiusMeters) {
}
