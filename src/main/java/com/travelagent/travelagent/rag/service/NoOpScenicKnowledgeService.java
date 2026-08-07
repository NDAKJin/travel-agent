package com.travelagent.travelagent.rag.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(ScenicKnowledgeService.class)
public class NoOpScenicKnowledgeService implements ScenicKnowledgeService {

    @Override
    public String buildContext(String query) {
        return "";
    }
}
