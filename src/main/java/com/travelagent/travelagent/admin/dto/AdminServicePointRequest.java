package com.travelagent.travelagent.admin.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

public record AdminServicePointRequest(
        @NotBlank(message = "便民服务名称不能为空") String name,
        @NotBlank(message = "便民服务类型不能为空") String category,
        @NotBlank(message = "便民服务介绍不能为空") String description,
        String address,
        @DecimalMin(value = "-180.0", message = "经度必须在-180到180之间")
        @DecimalMax(value = "180.0", message = "经度必须在-180到180之间") double longitude,
        @DecimalMin(value = "-90.0", message = "纬度必须在-90到90之间")
        @DecimalMax(value = "90.0", message = "纬度必须在-90到90之间") double latitude) {
}
