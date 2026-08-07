package com.travelagent.travelagent.auth.security;

public record AuthenticatedUser(long userId, String userType, String subject, String displayName) {
}
