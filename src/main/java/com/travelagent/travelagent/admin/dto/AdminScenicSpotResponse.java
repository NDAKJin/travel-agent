package com.travelagent.travelagent.admin.dto;

import java.time.Instant;

public record AdminScenicSpotResponse(String id, String name, String category, String description,
                                      double longitude, double latitude, Instant updatedAt) {
}
