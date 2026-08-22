package com.travelagent.travelagent.application.auth.model;

/** 当前请求的认证身份，由安全适配器创建，应用层只依赖这个最小模型。 */
public record AuthenticatedUser(long userId, String userType, String subject, String displayName) {
}
