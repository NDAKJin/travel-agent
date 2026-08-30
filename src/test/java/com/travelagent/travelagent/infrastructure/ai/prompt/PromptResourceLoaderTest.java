package com.travelagent.travelagent.infrastructure.ai.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PromptResourceLoaderTest {

    @Test
    void loadsSupervisorPromptFromResources() {
        assertThat(new PromptResourceLoader().load("intent-supervisor")).isNotBlank();
    }

    @Test
    void promptsUseStructuredChineseContracts() {
        PromptResourceLoader loader = new PromptResourceLoader();
        List.of("budget-agent", "finalize", "intent-supervisor", "normal-service",
                        "route-agent", "route-planner", "route-requirements", "route-reviewer")
                .forEach(name -> assertThat(loader.load(name)).isNotBlank());
    }
}
