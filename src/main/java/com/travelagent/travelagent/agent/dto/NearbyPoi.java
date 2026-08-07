package com.travelagent.travelagent.agent.dto;

public record NearbyPoi(String id, String name, String address, String description,
                        double latitude, double longitude, double distanceMeters) {
}
