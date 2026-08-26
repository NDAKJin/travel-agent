package com.travelagent.travelagent.application.agent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "travel-agent.agent")
@Getter
public class AgentProperties {
    private final ProfileProperties profile = new ProfileProperties();

    @Setter private int maxMessageChars = 4000;
    @Setter private int maxSessionIdChars = 64;
    @Setter private int maxHistoryMessages = 40;

    @Getter @Setter
    public static class ProfileProperties {
        private String name = "Travel Buddy";
    }

}
