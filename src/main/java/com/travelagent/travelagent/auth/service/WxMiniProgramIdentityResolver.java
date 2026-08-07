package com.travelagent.travelagent.auth.service;

public interface WxMiniProgramIdentityResolver {

    WxSessionIdentity resolve(String code);
}
