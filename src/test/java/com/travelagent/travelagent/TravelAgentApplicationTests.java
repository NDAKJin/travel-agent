package com.travelagent.travelagent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-key",
        "spring.mail.host=localhost",
        "spring.mail.port=2525",
        "travel-agent.studio.enabled=true"
})
class TravelAgentApplicationTests {

    @Test
    void contextLoads() {
    }

}
