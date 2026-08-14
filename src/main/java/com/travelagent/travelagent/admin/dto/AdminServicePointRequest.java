package com.travelagent.travelagent.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "便民服务点保存请求")
public record AdminServicePointRequest(
        @Schema(description = "服务点名称", example = "西湖游客中心") @NotBlank(message = "便民服务名称不能为空") String name,
        @Schema(description = "服务点分类", example = "游客中心") @NotBlank(message = "便民服务类型不能为空") String category,
        @Schema(description = "服务说明") @NotBlank(message = "便民服务介绍不能为空") String description,
        @Schema(description = "详细地址") String address,
        @DecimalMin(value = "-180.0", message = "经度必须在-180到180之间")
        @DecimalMax(value = "180.0", message = "经度必须在-180到180之间") @Schema(description = "经度", example = "120.1551") double longitude,
        @DecimalMin(value = "-90.0", message = "纬度必须在-90到90之间")
        @DecimalMax(value = "90.0", message = "纬度必须在-90到90之间") @Schema(description = "纬度", example = "30.2741") double latitude) {
}
