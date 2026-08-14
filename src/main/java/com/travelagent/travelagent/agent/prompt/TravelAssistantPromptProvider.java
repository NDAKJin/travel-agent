package com.travelagent.travelagent.agent.prompt;

import com.travelagent.travelagent.config.AgentProperties;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TravelAssistantPromptProvider implements PromptProvider {

    private final AgentProperties agentProperties;
    private final PromptResourceLoader promptResourceLoader;

    @Override
    public String systemPrompt() {
        String override = agentProperties.getPrompt().getOverride();
        return override != null && !override.isBlank() ? override : promptResourceLoader.load("supervisor");
    }
}
