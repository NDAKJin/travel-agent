package com.travelagent.travelagent.agent.controller;

import com.travelagent.travelagent.agent.dto.AgentChatRequest;
import com.travelagent.travelagent.agent.dto.AgentChatResponse;
import com.travelagent.travelagent.agent.dto.AgentSessionDetailResponse;
import com.travelagent.travelagent.agent.dto.AgentSessionSummaryResponse;
import com.travelagent.travelagent.agent.dto.NearbyNextPageRequest;
import com.travelagent.travelagent.agent.dto.NearbySearchResult;
import com.travelagent.travelagent.agent.service.NearbyPoiSearchService;
import com.travelagent.travelagent.agent.service.ReactAgentService;
import com.travelagent.travelagent.auth.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
@Tag(name = "Travel Agent", description = "Travel assistant chat API")
@Slf4j
@RequiredArgsConstructor
public class AgentController {

    private final ReactAgentService reactAgentService;
    private final NearbyPoiSearchService nearbyPoiSearchService;

    @PostMapping("/chat")
    @Operation(summary = "Chat with the agent", description = "Keeps multi-turn conversation by sessionId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public AgentChatResponse chat(@Valid @RequestBody AgentChatRequest request, Authentication authentication) {
        log.info("Agent chat request received: sessionId={}, messageLength={}",
                request.sessionId(),
                request.message() == null ? 0 : request.message().length());
        return reactAgentService.chat(currentUser(authentication), request);
    }

    @PostMapping("/sessions")
    @Operation(summary = "Create conversation session", description = "Creates an empty conversation session for the current user")
    public AgentSessionSummaryResponse createSession(Authentication authentication) {
        return reactAgentService.createSession(currentUser(authentication));
    }

    @GetMapping("/sessions")
    @Operation(summary = "List conversation sessions", description = "Lists previous conversations for the current user")
    public List<AgentSessionSummaryResponse> listSessions(Authentication authentication) {
        return reactAgentService.listSessions(currentUser(authentication));
    }

    @GetMapping("/sessions/{sessionId}")
    @Operation(summary = "Get conversation session detail", description = "Loads one previous conversation for the current user")
    public AgentSessionDetailResponse getSession(@PathVariable String sessionId, Authentication authentication) {
        return reactAgentService.getSession(currentUser(authentication), sessionId);
    }

    @PostMapping("/nearby/next")
    @Operation(summary = "Load the next page of nearby points of interest")
    public NearbySearchResult nearbyNext(@Valid @RequestBody NearbyNextPageRequest request) {
        return nearbyPoiSearchService.search(request.latitude(), request.longitude(), request.keyword(),
                request.radiusMeters() == null ? 20_000 : request.radiusMeters(), request.searchAfter());
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "Delete conversation session", description = "Deletes one conversation session for the current user")
    public void deleteSession(@PathVariable String sessionId, Authentication authentication) {
        reactAgentService.deleteSession(currentUser(authentication), sessionId);
    }

    private AuthenticatedUser currentUser(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
