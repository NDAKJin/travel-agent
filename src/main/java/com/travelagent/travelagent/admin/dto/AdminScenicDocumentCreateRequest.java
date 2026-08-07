package com.travelagent.travelagent.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminScenicDocumentCreateRequest(
        @NotBlank(message = "title must not be blank")
        String title,
        @NotBlank(message = "content must not be blank")
        String content) {
}
