package com.travelagent.travelagent.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "景点保存请求")
public record AdminScenicSpotRequest(
        @Schema(description = "景点名称", example = "西湖") @NotBlank(message = "景区名不能为空") String name,
        @Schema(description = "所属城市", example = "杭州") @NotBlank(message = "城市不能为空") String city,
        @Schema(description = "景点介绍") @NotBlank(message = "景区介绍不能为空") String description,
        @DecimalMin(value = "-180.0", message = "经度必须在 -180 到 180 之间")
        @DecimalMax(value = "180.0", message = "经度必须在 -180 到 180 之间") @Schema(description = "经度", example = "120.1551") double longitude,
        @DecimalMin(value = "-90.0", message = "纬度必须在 -90 到 90 之间")
        @DecimalMax(value = "90.0", message = "纬度必须在 -90 到 90 之间") @Schema(description = "纬度", example = "30.2741") double latitude) {
}
