package com.travelagent.travelagent.admin.dto;

import java.time.Instant;

public record AdminServicePointResponse(String id, String name, String category, String description,
                                        String address, double longitude, double latitude, Instant updatedAt) {
}
