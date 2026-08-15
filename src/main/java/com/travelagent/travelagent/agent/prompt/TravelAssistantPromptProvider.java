package com.travelagent.travelagent.agent.prompt;

import com.travelagent.travelagent.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class TravelAssistantPromptProvider {

    private final AgentProperties agentProperties;
    private final PromptResourceLoader promptResourceLoader;

    public String systemPrompt() {
        String override = agentProperties.getPrompt().getOverride();
        return override != null && !override.isBlank() ? override : promptResourceLoader.load("supervisor");
    }
}
