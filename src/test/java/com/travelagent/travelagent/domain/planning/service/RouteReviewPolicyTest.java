package com.travelagent.travelagent.domain.planning.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RouteReviewPolicyTest {

    private final RouteReviewPolicy policy = new RouteReviewPolicy(2);

    @Test
    void allowsOnlyTheConfiguredNumberOfRevisions() {
        assertTrue(policy.shouldReplan(false, 1));
        assertTrue(policy.shouldReplan(false, 2));
        assertFalse(policy.shouldReplan(false, 3));
        assertFalse(policy.shouldReplan(true, 1));
    }
}
