package com.travelagent.travelagent.common.dto;

import java.util.List;

public record PageResponse<T>(
        List<T> content,
        long total,
        int page,
        int size) {
}
