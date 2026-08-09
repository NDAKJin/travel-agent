package com.travelagent.travelagent.admin.dto;

public record AdminMapPlaceResponse(
        String id,
        String name,
        String address,
        double longitude,
        double latitude
) {
}
