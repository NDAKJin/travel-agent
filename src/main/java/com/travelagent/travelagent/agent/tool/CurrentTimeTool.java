package com.travelagent.travelagent.agent.tool;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CurrentTimeTool {
    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Autowired
    private Clock clock;

    @Tool(name = "current_time", description = "查询当前日期和时间。用于处理时间敏感的问题、截止时间、日程安排，或用户直接询问当前时间。")
    public String currentTime() {
        log.info("Executing tool: current_time");
        try {
            Instant now = clock.instant();
            ZonedDateTime utcTime = now.atZone(ZoneId.of("UTC"));
            ZonedDateTime shanghaiTime = now.atZone(SHANGHAI_ZONE);
            log.debug("Tool current_time completed: utc={}, shanghai={}",
                    FORMATTER.format(utcTime),
                    FORMATTER.format(shanghaiTime));
            return """
                    当前时间信息：
                    - UTC：%s
                    - 亚洲/上海：%s
                    - Unix 时间戳（毫秒）：%d
                    """.formatted(
                    FORMATTER.format(utcTime),
                    FORMATTER.format(shanghaiTime),
                    now.toEpochMilli());
        }
        catch (RuntimeException exception) {
            log.error("Tool current_time failed", exception);
            throw exception;
        }
    }
}
