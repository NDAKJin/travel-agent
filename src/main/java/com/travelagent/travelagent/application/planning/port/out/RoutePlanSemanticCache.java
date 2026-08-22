package com.travelagent.travelagent.application.planning.port.out;

import java.util.Optional;

public interface RoutePlanSemanticCache {
    Optional<Hit> find(String requirements);
    void put(String requirements, String routePlan);

    record Hit(String routePlan, double score) {
    }
}
