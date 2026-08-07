package com.travelagent.travelagent.auth.model;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminUser {

    private Long id;
    private String username;
    private String passwordHash;
    private String displayName;
    private boolean enabled = true;
    private Instant createdAt;
    private Instant updatedAt;
}
