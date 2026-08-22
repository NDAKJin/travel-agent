package com.travelagent.travelagent.domain.planning.service;

/** 路线需求的最小领域规则。具体字段解析仍由工作流适配器负责。 */
public final class RequirementPolicy {

    private RequirementPolicy() {
    }

    public static boolean isConfirmed(String status) {
        return "confirmed".equalsIgnoreCase(status);
    }
}
