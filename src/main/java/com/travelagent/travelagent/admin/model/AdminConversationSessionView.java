package com.travelagent.travelagent.admin.model;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminConversationSessionView {

    private Long id;
    private Long userId;
    private String userType;
    private String userSubject;
    private String userDisplayName;
    private String sessionId;
    private String title;
    private String preview;
    private String messagesJson;
    private int messageCount;
    private Instant createdAt;
    private Instant updatedAt;
}
