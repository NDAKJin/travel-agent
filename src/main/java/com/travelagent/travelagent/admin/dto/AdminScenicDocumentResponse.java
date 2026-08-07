package com.travelagent.travelagent.admin.dto;

import java.time.Instant;

public record AdminScenicDocumentResponse(String fileName, String path, Instant updatedAt) {
}
