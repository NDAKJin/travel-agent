package com.travelagent.travelagent.agent.subagent;

import com.travelagent.travelagent.agent.prompt.PromptResourceLoader;
import com.travelagent.travelagent.agent.service.SpecialistAgentRunner;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class PoiSearchAgent {

    private final ChatClient chatClient;
    private final PromptResourceLoader promptResourceLoader;
    private final SpecialistAgentRunner runner;

    public PoiSearchAgent(@Qualifier("poiSearchChatClient") ChatClient chatClient,
                          PromptResourceLoader promptResourceLoader, SpecialistAgentRunner runner) {
        this.chatClient = chatClient;
        this.promptResourceLoader = promptResourceLoader;
        this.runner = runner;
    }

    @Tool(name = "delegate_poi_search", description = "委派给 POI 搜索专员，查询用户当前位置附近的景点、餐厅、酒店或便民服务。")
    public String searchPoi(String task) {
        return runner.run("poi", chatClient, promptResourceLoader.load("poi-agent"), task);
    }
}
