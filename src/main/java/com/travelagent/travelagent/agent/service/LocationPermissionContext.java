package com.travelagent.travelagent.agent.service;

public final class LocationPermissionContext {
    private static final ThreadLocal<Boolean> REQUESTED = new ThreadLocal<>();

    private LocationPermissionContext() {
    }

    public static void request() {
        REQUESTED.set(Boolean.TRUE);
    }

    public static boolean isRequested() {
        return Boolean.TRUE.equals(REQUESTED.get());
    }

    public static void clear() {
        REQUESTED.remove();
    }
}
