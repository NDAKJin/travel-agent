package com.travelagent.travelagent.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpecialistAgentRunnerTest {

    @Test
    void preservesMarkdownSpecialistOutput() {
        String result = SpecialistAgentRunner.normalize("""
                结论：已找到路线
                - 路线：故宫 → 景山公园
                """);

        assertThat(result).isEqualTo("结论：已找到路线\n- 路线：故宫 → 景山公园");
    }

    @Test
    void convertsNullSpecialistOutputToEmptyText() {
        assertThat(SpecialistAgentRunner.normalize(null)).isEmpty();
    }
}
