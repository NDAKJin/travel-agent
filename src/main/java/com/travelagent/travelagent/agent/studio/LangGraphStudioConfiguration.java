package com.travelagent.travelagent.agent.studio;

import com.alibaba.fastjson2.JSON;
import com.travelagent.travelagent.agent.model.AgentMessage;
import com.travelagent.travelagent.agent.service.LangGraphTravelAgent;
import java.util.List;
import java.util.Map;
import org.bsc.langgraph4j.studio.LangGraphStudioServer;
import org.bsc.langgraph4j.studio.springboot.LangGraphStudioConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "travel-agent.studio", name = "enabled", havingValue = "true")
public class LangGraphStudioConfiguration extends LangGraphStudioConfig {

    private final LangGraphTravelAgent travelAgent;

    public LangGraphStudioConfiguration(LangGraphTravelAgent travelAgent) {
        this.travelAgent = travelAgent;
    }

    @Override
    public Map<String, LangGraphStudioServer.Instance> instanceMap() {
        return Map.of("travel-agent", LangGraphStudioServer.Instance.builder()
                .title("旅行助手 Agent 编排")
                .graph(travelAgent.studioWorkflow())
                .compileConfig(travelAgent.studioCompileConfig())
                .addInputStringArg("history", true, this::toHistory)
                .build());
    }

    private List<AgentMessage> toHistory(Object value) {
        return JSON.parseArray(String.valueOf(value), AgentMessage.class);
    }
}
