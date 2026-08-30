package com.travelagent.travelagent.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpecialistAgentRunnerTest {

    @Test
    void preservesMarkdownSpecialistOutput() {
        String result = SpecialistAgentRunner.normalize("""
                缁撹锛氬凡鎵惧埌璺嚎
                - 璺嚎锛氭晠瀹?鈫?鏅北鍏洯
                """);

        assertThat(result).isEqualTo("缁撹锛氬凡鎵惧埌璺嚎\n- 璺嚎锛氭晠瀹?鈫?鏅北鍏洯");
    }

    @Test
    void convertsNullSpecialistOutputToEmptyText() {
        assertThat(SpecialistAgentRunner.normalize(null)).isEmpty();
    }
}
