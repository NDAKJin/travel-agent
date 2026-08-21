package com.travelagent.travelagent.application.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "分页响应")
public record PageResponse<T>(
        @Schema(description = "当前页数据") List<T> content,
        @Schema(description = "总记录数") long total,
        @Schema(description = "当前页码，从 1 开始") int page,
        @Schema(description = "每页数量") int size) {
}
