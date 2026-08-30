package com.travelagent.travelagent.domain.auth.service;

public interface WxMiniProgramIdentityResolver {

    WxSessionIdentity resolve(String code);
}
