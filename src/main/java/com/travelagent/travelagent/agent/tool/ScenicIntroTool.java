package com.travelagent.travelagent.agent.tool;

import com.travelagent.travelagent.rag.service.ScenicKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
@RequiredArgsConstructor
public class ScenicIntroTool {

    private final ScenicKnowledgeService scenicKnowledgeService;

    @Tool(name = "scenic_intro", description = "检索景区知识库，获取景点介绍、亮点、对比和游览建议。仅当用户询问具体景区或要求介绍某个景点时使用。")
    public String scenicIntro(String query) {
        log.info("Executing tool: scenic_intro, query={}", query);
        if (!StringUtils.hasText(query)) {
            return "请提供景区名称或具体的景点问题。";
        }
        try {
            String context = scenicKnowledgeService.buildContext(query);
            if (!StringUtils.hasText(context)) {
                return "没有找到匹配的景区知识。";
            }
            return context;
        }
        catch (RuntimeException exception) {
            log.error("Tool scenic_intro failed", exception);
            return "景区知识库暂时不可用。";
        }
    }
}
