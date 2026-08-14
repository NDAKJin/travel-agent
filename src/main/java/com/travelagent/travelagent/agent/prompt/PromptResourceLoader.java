package com.travelagent.travelagent.agent.prompt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
@Slf4j
public class PromptResourceLoader {

    public String load(String name) {
        try {
            return StreamUtils.copyToString(new ClassPathResource("prompt/" + name + ".md").getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            log.error("Failed to load prompt resource: {}", name, exception);
            throw new IllegalStateException("Prompt resource is unavailable: " + name, exception);
        }
    }
}
