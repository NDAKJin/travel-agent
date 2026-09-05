package com.travelagent.travelagent.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "travel-agent.agent")
@Getter
public class AgentProperties {
    private final ProfileProperties profile = new ProfileProperties();

    @Setter private int maxMessageChars = 4000;
    @Setter private int maxSessionIdChars = 64;
    /** Maximum estimated input tokens allocated to conversation history. */
    @Setter private int maxHistoryTokens = 420000;
    @Setter private double summaryTriggerRatio = 0.70d;
    @Setter private int summaryTargetTokens = 12000;
    @Setter private int recentMessageTokens = 30000;

    @Getter @Setter
    public static class ProfileProperties {
        private String name = "Travel Buddy";
    }

}
