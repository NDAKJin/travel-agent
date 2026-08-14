package com.travelagent.travelagent.agent.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PromptResourceLoaderTest {

    @Test
    void loadsSupervisorPromptFromResources() {
        assertThat(new PromptResourceLoader().load("supervisor")).contains("旅行规划总控智能体");
    }
}
