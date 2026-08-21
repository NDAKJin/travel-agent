package com.travelagent.travelagent.domain.planning.service;

/**
 * 路线审核领域规则：审核不通过时允许有限次数回流规划师，避免流程无限循环。
 */
public record RouteReviewPolicy(int maxRevisions) {

    public RouteReviewPolicy {
        if (maxRevisions < 0) {
            throw new IllegalArgumentException("maxRevisions must not be negative");
        }
    }

    public static RouteReviewPolicy standard() {
        return new RouteReviewPolicy(2);
    }

    public boolean shouldReplan(boolean approved, int reviewAttempts) {
        return !approved && reviewAttempts <= maxRevisions;
    }

    public boolean mustFinalize(boolean approved, int reviewAttempts) {
        return approved || reviewAttempts > maxRevisions;
    }
}
