package com.travelagent.travelagent.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminLoginRequest(@NotBlank String username,
                                @NotBlank String password) {
}
