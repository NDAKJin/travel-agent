package com.travelagent.travelagent.domain.auth.model;

public record AuthenticatedAccount(Long id,
                                   String userType,
                                   String subject,
                                   String displayName) {
}
