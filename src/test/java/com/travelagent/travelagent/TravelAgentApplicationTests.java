package com.travelagent.travelagent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-key",
        "travel-agent.studio.enabled=true"
})
class TravelAgentApplicationTests {

    @Test
    void contextLoads() {
    }

}
