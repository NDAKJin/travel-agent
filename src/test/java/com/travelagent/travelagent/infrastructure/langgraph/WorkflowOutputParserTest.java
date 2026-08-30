package com.travelagent.travelagent.infrastructure.langgraph;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class WorkflowOutputParserTest {
    @Test void parsesIntentAndFallsBackToNormal() {
        assertThat(WorkflowOutputParser.intent("{\"intent\":\"ROUTE\"}")).isEqualTo("route");
        assertThat(WorkflowOutputParser.intent("bad")).isEqualTo("normal");
    }

    @Test void validatesRequirementsAndFallback() {
        String valid = "{\"status\":\"CONFIRMED\",\"requirements\":{"
                + "\"origin\":\"A\",\"destination\":\"B\",\"date\":\"2026\","
                + "\"days\":null,\"people\":\"2\",\"budget\":\"100\"}}";
        assertThat(WorkflowOutputParser.requirements(valid).confirmed()).isTrue();
        assertThat(WorkflowOutputParser.requirements("{}").confirmed()).isFalse();
    }

    @Test void parsesReviewAndPlannerDecisions() {
        assertThat(WorkflowOutputParser.review("{\"status\":\"APPROVED\",\"issues\":[]}").approved()).isTrue();
        assertThat(WorkflowOutputParser.review("bad").approved()).isFalse();
        String delegate = "{\"action\":\"DELEGATE\",\"tasks\":[{\"expert\":\"knowledge\",\"task\":{}}]}";
        assertThat(WorkflowOutputParser.planner(delegate).delegates()).isTrue();
        assertThat(WorkflowOutputParser.planner("{\"plan\":{}}").next()).isEqualTo("routeReviewer");
    }
}
