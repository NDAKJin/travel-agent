package com.travelagent.travelagent.auth.model;

public record AuthenticatedAccount(Long id,
                                   String userType,
                                   String subject,
                                   String displayName) {
}
