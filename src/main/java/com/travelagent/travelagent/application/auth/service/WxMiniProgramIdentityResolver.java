package com.travelagent.travelagent.application.auth.service;

public interface WxMiniProgramIdentityResolver {

    WxSessionIdentity resolve(String code);
}
