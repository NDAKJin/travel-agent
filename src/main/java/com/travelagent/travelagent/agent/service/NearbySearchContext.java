package com.travelagent.travelagent.agent.service;

import com.travelagent.travelagent.agent.dto.NearbySearchResult;

public final class NearbySearchContext {
    private static final ThreadLocal<NearbySearchResult> CURRENT = new ThreadLocal<>();

    private NearbySearchContext() {
    }

    public static void set(NearbySearchResult result) { CURRENT.set(result); }

    public static NearbySearchResult get() { return CURRENT.get(); }

    public static void clear() { CURRENT.remove(); }
}
