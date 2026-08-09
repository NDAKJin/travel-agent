package com.travelagent.travelagent.admin.controller;

import com.travelagent.travelagent.agent.dto.AgentSessionDetailResponse;
import com.travelagent.travelagent.admin.dto.AdminConversationSummaryResponse;
import com.travelagent.travelagent.admin.dto.AdminMapPlaceResponse;
import com.travelagent.travelagent.admin.dto.AdminScenicDocumentCreateRequest;
import com.travelagent.travelagent.admin.dto.AdminScenicDocumentResponse;
import com.travelagent.travelagent.admin.dto.AdminScenicSpotRequest;
import com.travelagent.travelagent.admin.dto.AdminScenicSpotResponse;
import com.travelagent.travelagent.admin.dto.AdminWxUserResponse;
import com.travelagent.travelagent.admin.service.AdminManagementService;
import com.travelagent.travelagent.admin.service.AmapPlaceSearchService;
import com.travelagent.travelagent.admin.service.ScenicSpotGeoService;
import com.travelagent.travelagent.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminManagementService adminManagementService;
    private final ScenicSpotGeoService scenicSpotGeoService;
    private final AmapPlaceSearchService amapPlaceSearchService;

    @GetMapping("/wx-users")
    @Operation(summary = "Search wx users")
    public PageResponse<AdminWxUserResponse> searchWxUsers(@RequestParam(required = false) String keyword,
                                                           @RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return adminManagementService.searchWxUsers(keyword, page, size);
    }

    @GetMapping("/sessions")
    @Operation(summary = "List sessions")
    public PageResponse<AdminConversationSummaryResponse> listSessions(@RequestParam(required = false) Long wxUserId,
                                                                       @RequestParam(defaultValue = "1") int page,
                                                                       @RequestParam(defaultValue = "20") int size) {
        return adminManagementService.listSessions(wxUserId, page, size);
    }

    @GetMapping("/sessions/{conversationId}")
    @Operation(summary = "Get session detail")
    public AgentSessionDetailResponse getSessionDetail(@PathVariable long conversationId) {
        return adminManagementService.getSessionDetail(conversationId);
    }

    @PostMapping("/rag/scenic-documents")
    @Operation(summary = "Add a scenic markdown document to RAG")
    public AdminScenicDocumentResponse addScenicDocument(@Valid @RequestBody AdminScenicDocumentCreateRequest request) {
        return adminManagementService.addScenicDocument(request);
    }

    @GetMapping("/scenic-spots")
    @Operation(summary = "List scenic spots")
    public List<AdminScenicSpotResponse> listScenicSpots() {
        return scenicSpotGeoService.list();
    }

    @GetMapping("/scenic-spots/{id}")
    @Operation(summary = "Get scenic spot detail")
    public AdminScenicSpotResponse getScenicSpot(@PathVariable String id) {
        return scenicSpotGeoService.get(id);
    }

    @GetMapping("/scenic-spots/nearby")
    @Operation(summary = "Find nearby scenic spots")
    public List<AdminScenicSpotResponse> nearbyScenicSpots(@RequestParam double longitude,
                                                           @RequestParam double latitude,
                                                           @RequestParam(defaultValue = "20km") String distance) {
        return scenicSpotGeoService.nearby(longitude, latitude, distance);
    }

    @GetMapping("/map/places")
    @Operation(summary = "Search Amap places")
    public List<AdminMapPlaceResponse> searchMapPlaces(@RequestParam String keyword) {
        return amapPlaceSearchService.search(keyword);
    }

    @PostMapping("/scenic-spots")
    @Operation(summary = "Create scenic spot")
    public AdminScenicSpotResponse createScenicSpot(@Valid @RequestBody AdminScenicSpotRequest request) {
        return scenicSpotGeoService.save(null, request);
    }

    @PutMapping("/scenic-spots/{id}")
    @Operation(summary = "Update scenic spot")
    public AdminScenicSpotResponse updateScenicSpot(@PathVariable String id,
                                                    @Valid @RequestBody AdminScenicSpotRequest request) {
        return scenicSpotGeoService.save(id, request);
    }

    @DeleteMapping("/scenic-spots/{id}")
    @Operation(summary = "Delete scenic spot")
    public void deleteScenicSpot(@PathVariable String id) {
        scenicSpotGeoService.delete(id);
    }
}
