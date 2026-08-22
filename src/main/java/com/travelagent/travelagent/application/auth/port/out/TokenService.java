package com.travelagent.travelagent.application.auth.port.out;

import com.travelagent.travelagent.domain.auth.model.DecodedToken;
import com.travelagent.travelagent.domain.auth.model.TokenPair;

public interface TokenService {
    TokenPair issueTokenPair(long userId, String userType, String subject, String displayName);
    DecodedToken decodeAndVerify(String token);
}
