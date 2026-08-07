package com.travelagent.travelagent.agent.dto;

import java.util.List;

public record NearbyNextPageRequest(double latitude, double longitude, String keyword,
                                    Integer radiusMeters, List<Object> searchAfter) {
}
