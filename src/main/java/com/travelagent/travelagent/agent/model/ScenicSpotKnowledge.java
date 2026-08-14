package com.travelagent.travelagent.agent.model;

import java.util.List;

public record ScenicSpotKnowledge(String id, String name, String city, String description,
                                  double longitude, double latitude, double score,
                                  List<String> nearbySpotNames) {
}
