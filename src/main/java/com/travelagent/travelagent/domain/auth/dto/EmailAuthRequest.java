package com.travelagent.travelagent.domain.auth.dto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
public record EmailAuthRequest(@Email @NotBlank String email, String phone, @NotBlank String code) { }
