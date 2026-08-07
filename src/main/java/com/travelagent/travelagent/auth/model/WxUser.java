package com.travelagent.travelagent.auth.model;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WxUser {

    private Long id;
    private String openId;
    private String nickname;
    private String avatarUrl;
    private boolean enabled = true;
    private Instant createdAt;
    private Instant updatedAt;
}
