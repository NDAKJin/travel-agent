package com.travelagent.travelagent.rag.service;

import com.travelagent.travelagent.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScenicKnowledgeBootstrap implements ApplicationRunner {

    private final ObjectProvider<ScenicKnowledgeIngestionService> scenicKnowledgeIngestionServiceProvider;
    private final AgentProperties agentProperties;

    @Override
    public void run(ApplicationArguments args) {
        if (agentProperties.getRag().isInitializeOnStartup()) {
            try {
                ScenicKnowledgeIngestionService scenicKnowledgeIngestionService =
                        scenicKnowledgeIngestionServiceProvider.getIfAvailable();
                if (scenicKnowledgeIngestionService != null) {
                    scenicKnowledgeIngestionService.ingestAll();
                }
            }
            catch (RuntimeException exception) {
                log.warn("Skip scenic knowledge bootstrap because Elasticsearch is unavailable", exception);
            }
        }
    }
}
