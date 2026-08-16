package com.travelagent.travelagent.admin.controller;

import com.travelagent.travelagent.admin.dto.AdminConversationSummaryResponse;
import com.travelagent.travelagent.admin.dto.AdminConversationDetailResponse;
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
import com.travelagent.travelagent.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "管理端", description = "景点、便民服务、用户与会话管理")
public class AdminController {

    private final AdminManagementService adminManagementService;
    private final ScenicSpotGeoService scenicSpotGeoService;
    private final AmapPlaceSearchService amapPlaceSearchService;
    private final ServicePointGeoService servicePointGeoService;
    private final ServicePointCategoryService servicePointCategoryService;
    private final ObjectProvider<ScenicSpotKnowledgeGraphService> scenicSpotKnowledgeGraphServiceProvider;

    @GetMapping("/service-point-categories")
    @Operation(summary = "查询便民服务分类")
    public List<String> listServicePointCategories() { return servicePointCategoryService.list(); }

    @PostMapping("/service-point-categories")
    @Operation(summary = "新增便民服务分类")
    public String createServicePointCategory(@Parameter(description = "分类名称", example = "停车场") @RequestBody String name) {
        return servicePointCategoryService.create(name.replaceAll("^\"|\"$", ""));
    }

    @GetMapping("/service-points")
    @Operation(summary = "分页查询便民服务点")
    public PageResponse<AdminServicePointResponse> listServicePoints(
            @Parameter(description = "服务点分类；为空时查询全部") @RequestParam(required = false) String category,
            @Parameter(description = "页码，从 1 开始", example = "1") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页数量，最大 100", example = "20") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return servicePointGeoService.list(category, page, size);
    }

    @GetMapping("/service-points/{id}")
    @Operation(summary = "查询便民服务点详情")
    public AdminServicePointResponse getServicePoint(@Parameter(description = "服务点标识") @PathVariable String id) { return servicePointGeoService.get(id); }

    @PostMapping("/service-points")
    @Operation(summary = "新增便民服务点")
    public AdminServicePointResponse createServicePoint(@Valid @RequestBody AdminServicePointRequest request) {
        return servicePointGeoService.save(null, request);
    }

    @PutMapping("/service-points/{id}")
    @Operation(summary = "更新便民服务点")
    public AdminServicePointResponse updateServicePoint(@Parameter(description = "服务点标识") @PathVariable String id, @Valid @RequestBody AdminServicePointRequest request) {
        return servicePointGeoService.save(id, request);
    }

    @DeleteMapping("/service-points/{id}")
    @Operation(summary = "删除便民服务点")
    public void deleteServicePoint(@Parameter(description = "服务点标识") @PathVariable String id) { servicePointGeoService.delete(id); }

    @GetMapping("/wx-users")
    @Operation(summary = "分页查询微信用户")
    public PageResponse<AdminWxUserResponse> searchWxUsers(@Parameter(description = "用户昵称或 OpenID 关键词") @RequestParam(required = false) String keyword,
                                                           @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") @Min(1) int page,
                                                           @Parameter(description = "每页数量，最大 100") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return adminManagementService.searchWxUsers(keyword, page, size);
    }

    @GetMapping("/sessions")
    @Operation(summary = "分页查询用户会话")
    public PageResponse<AdminConversationSummaryResponse> listSessions(@Parameter(description = "微信用户标识；为空时查询全部") @RequestParam(required = false) Long wxUserId,
                                                                       @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") @Min(1) int page,
                                                                       @Parameter(description = "每页数量，最大 100") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return adminManagementService.listSessions(wxUserId, page, size);
    }

    @GetMapping("/sessions/{conversationId}")
    @Operation(summary = "查询管理端会话详情")
    public AdminConversationDetailResponse getSessionDetail(@Parameter(description = "会话数据库标识") @PathVariable long conversationId) {
        return adminManagementService.getSessionDetail(conversationId);
    }

    @GetMapping("/scenic-spots")
    @Operation(summary = "查询全部景点")
    public List<AdminScenicSpotResponse> listScenicSpots() { return scenicSpotGeoService.list(); }

    @GetMapping("/scenic-spots/{id}")
    @Operation(summary = "查询景点详情")
    public AdminScenicSpotResponse getScenicSpot(@Parameter(description = "景点标识") @PathVariable String id) { return scenicSpotGeoService.get(id); }

    @GetMapping("/scenic-spots/nearby")
    @Operation(summary = "查询指定坐标附近景点")
    public List<AdminScenicSpotResponse> nearbyScenicSpots(@Parameter(description = "查询中心经度", example = "120.1551") @RequestParam double longitude,
                                                           @Parameter(description = "查询中心纬度", example = "30.2741") @RequestParam double latitude,
                                                           @Parameter(description = "搜索距离", example = "20km") @RequestParam(defaultValue = "20km") String distance) {
        return scenicSpotGeoService.nearby(longitude, latitude, distance);
    }

    @GetMapping("/map/places")
    @Operation(summary = "搜索地图地点")
    public List<AdminMapPlaceResponse> searchMapPlaces(@Parameter(description = "地图搜索关键词", example = "西湖") @RequestParam String keyword) {
        return amapPlaceSearchService.search(keyword);
    }

    @PostMapping("/scenic-spots")
    @Operation(summary = "新增景点")
    public AdminScenicSpotResponse createScenicSpot(@Valid @RequestBody AdminScenicSpotRequest request) {
        return scenicSpotGeoService.save(null, request);
    }

    @PutMapping("/scenic-spots/{id}")
    @Operation(summary = "更新景点")
    public AdminScenicSpotResponse updateScenicSpot(@Parameter(description = "景点标识") @PathVariable String id, @Valid @RequestBody AdminScenicSpotRequest request) {
        return scenicSpotGeoService.save(id, request);
    }

    @DeleteMapping("/scenic-spots/{id}")
    @Operation(summary = "删除景点")
    public void deleteScenicSpot(@Parameter(description = "景点标识") @PathVariable String id) { scenicSpotGeoService.delete(id); }

    @PostMapping("/scenic-spots/reindex-knowledge")
    @Operation(summary = "重建景点知识图谱")
    public void reindexScenicSpotKnowledge() {
        ScenicSpotKnowledgeGraphService knowledgeGraphService = scenicSpotKnowledgeGraphServiceProvider.getIfAvailable();
        if (knowledgeGraphService == null) throw new IllegalStateException("Neo4j knowledge graph is disabled");
        knowledgeGraphService.reindex(scenicSpotGeoService.listForKnowledge());
    }
}
