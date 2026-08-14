package com.travelagent.travelagent.admin.controller;

import com.travelagent.travelagent.admin.dto.AdminConversationSummaryResponse;
import com.travelagent.travelagent.admin.dto.AdminMapPlaceResponse;
import com.travelagent.travelagent.admin.dto.AdminScenicSpotRequest;
import com.travelagent.travelagent.admin.dto.AdminScenicSpotResponse;
import com.travelagent.travelagent.admin.dto.AdminServicePointRequest;
import com.travelagent.travelagent.admin.dto.AdminServicePointResponse;
import com.travelagent.travelagent.admin.dto.AdminWxUserResponse;
import com.travelagent.travelagent.admin.service.AdminManagementService;
import com.travelagent.travelagent.admin.service.AmapPlaceSearchService;
import com.travelagent.travelagent.admin.service.ScenicSpotGeoService;
import com.travelagent.travelagent.admin.service.ServicePointCategoryService;
import com.travelagent.travelagent.admin.service.ServicePointGeoService;
import com.travelagent.travelagent.agent.service.ScenicSpotKnowledgeGraphService;
import com.travelagent.travelagent.agent.dto.AgentSessionDetailResponse;
import com.travelagent.travelagent.common.dto.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Validated
public class AdminController {

    private final AdminManagementService adminManagementService;
    private final ScenicSpotGeoService scenicSpotGeoService;
    private final AmapPlaceSearchService amapPlaceSearchService;
    private final ServicePointGeoService servicePointGeoService;
    private final ServicePointCategoryService servicePointCategoryService;
    private final ObjectProvider<ScenicSpotKnowledgeGraphService> scenicSpotKnowledgeGraphServiceProvider;

    @GetMapping("/service-point-categories")
    public List<String> listServicePointCategories() { return servicePointCategoryService.list(); }

    @PostMapping("/service-point-categories")
    public String createServicePointCategory(@RequestBody String name) {
        return servicePointCategoryService.create(name.replaceAll("^\"|\"$", ""));
    }

    @GetMapping("/service-points")
    public PageResponse<AdminServicePointResponse> listServicePoints(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return servicePointGeoService.list(category, page, size);
    }

    @GetMapping("/service-points/{id}")
    public AdminServicePointResponse getServicePoint(@PathVariable String id) { return servicePointGeoService.get(id); }

    @PostMapping("/service-points")
    public AdminServicePointResponse createServicePoint(@Valid @RequestBody AdminServicePointRequest request) {
        return servicePointGeoService.save(null, request);
    }

    @PutMapping("/service-points/{id}")
    public AdminServicePointResponse updateServicePoint(@PathVariable String id, @Valid @RequestBody AdminServicePointRequest request) {
        return servicePointGeoService.save(id, request);
    }

    @DeleteMapping("/service-points/{id}")
    public void deleteServicePoint(@PathVariable String id) { servicePointGeoService.delete(id); }

    @GetMapping("/wx-users")
    public PageResponse<AdminWxUserResponse> searchWxUsers(@RequestParam(required = false) String keyword,
                                                           @RequestParam(defaultValue = "1") @Min(1) int page,
                                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return adminManagementService.searchWxUsers(keyword, page, size);
    }

    @GetMapping("/sessions")
    public PageResponse<AdminConversationSummaryResponse> listSessions(@RequestParam(required = false) Long wxUserId,
                                                                       @RequestParam(defaultValue = "1") @Min(1) int page,
                                                                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return adminManagementService.listSessions(wxUserId, page, size);
    }

    @GetMapping("/sessions/{conversationId}")
    public AgentSessionDetailResponse getSessionDetail(@PathVariable long conversationId) {
        return adminManagementService.getSessionDetail(conversationId);
    }

    @GetMapping("/scenic-spots")
    public List<AdminScenicSpotResponse> listScenicSpots() { return scenicSpotGeoService.list(); }

    @GetMapping("/scenic-spots/{id}")
    public AdminScenicSpotResponse getScenicSpot(@PathVariable String id) { return scenicSpotGeoService.get(id); }

    @GetMapping("/scenic-spots/nearby")
    public List<AdminScenicSpotResponse> nearbyScenicSpots(@RequestParam double longitude,
                                                           @RequestParam double latitude,
                                                           @RequestParam(defaultValue = "20km") String distance) {
        return scenicSpotGeoService.nearby(longitude, latitude, distance);
    }

    @GetMapping("/map/places")
    public List<AdminMapPlaceResponse> searchMapPlaces(@RequestParam String keyword) {
        return amapPlaceSearchService.search(keyword);
    }

    @PostMapping("/scenic-spots")
    public AdminScenicSpotResponse createScenicSpot(@Valid @RequestBody AdminScenicSpotRequest request) {
        return scenicSpotGeoService.save(null, request);
    }

    @PutMapping("/scenic-spots/{id}")
    public AdminScenicSpotResponse updateScenicSpot(@PathVariable String id, @Valid @RequestBody AdminScenicSpotRequest request) {
        return scenicSpotGeoService.save(id, request);
    }

    @DeleteMapping("/scenic-spots/{id}")
    public void deleteScenicSpot(@PathVariable String id) { scenicSpotGeoService.delete(id); }

    @PostMapping("/scenic-spots/reindex-knowledge")
    public void reindexScenicSpotKnowledge() {
        ScenicSpotKnowledgeGraphService knowledgeGraphService = scenicSpotKnowledgeGraphServiceProvider.getIfAvailable();
        if (knowledgeGraphService == null) throw new IllegalStateException("Neo4j knowledge graph is disabled");
        knowledgeGraphService.reindex(scenicSpotGeoService.listForKnowledge());
    }
}
