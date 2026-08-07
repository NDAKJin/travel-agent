package com.travelagent.travelagent.agent.service;

import com.travelagent.travelagent.agent.dto.AgentUserLocation;

public final class CurrentUserLocationContext {
    private static final ThreadLocal<AgentUserLocation> CURRENT = new ThreadLocal<>();

    private CurrentUserLocationContext() {
    }

    public static void set(AgentUserLocation location) {
        if (location == null) CURRENT.remove();
        else CURRENT.set(location);
    }

    public static AgentUserLocation get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
