// ============================================================================
// GPU Management Controller - REST API Layer
// ============================================================================

package com.k8s.monitor.controller.gpu;

import com.k8s.monitor.dto.gpu.*;
import com.k8s.monitor.service.gpu.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * GPU 장비 관리 REST API 컨트롤러
 * GPU 장비 등록, 조회, 상태 관리 등의 API 제공
 */
@RestController
@RequestMapping("/api/v1/gpu")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class GpuManagementController {
    
    private final GpuDeviceService gpuDeviceService;
    private final MigManagementService migManagementService;
    private final GpuAllocationService allocationService;
    private final GpuMetricsCollectionService metricsService;

    /**
     * GPU 클러스터 전체 개요 조회
     */
    @GetMapping("/overview")
    public ResponseEntity<GpuClusterOverview> getClusterOverview() {
        log.info("Fetching GPU cluster overview");
        
        try {
            // 여기서는 서비스 메서드들을 조합하여 클러스터 개요 생성
            GpuClusterOverview overview = buildClusterOverview();
            return ResponseEntity.ok(overview);
        } catch (Exception e) {
            log.error("Error fetching GPU cluster overview: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 모든 GPU 장비 조회
     */
    @GetMapping("/devices")
    public ResponseEntity<List<GpuDeviceInfo>> getAllGpuDevices(
            @RequestParam(required = false) String nodeName,
            @RequestParam(required = false) String modelId,
            @RequestParam(required = false) String status) {
        log.info("Fetching GPU devices - node: {}, model: {}, status: {}", nodeName, modelId, status);
        
        try {
            List<GpuDeviceInfo> devices;
            
            if (nodeName != null) {
                devices = gpuDeviceService.getGpuDevicesByNode(nodeName);
            } else if (modelId != null) {
                devices = gpuDeviceService.getGpuDevicesByModel(modelId);
            } else if ("available".equals(status)) {
                devices = gpuDeviceService.getAvailableGpuDevices();
            } else {
                devices = gpuDeviceService.getAllGpuDevices();
            }
            
            return ResponseEntity.ok(devices);
        } catch (Exception e) {
            log.error("Error fetching GPU devices: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 특정 GPU 장비 상세 정보 조회
     */
    @GetMapping("/devices/{deviceId}")
    public ResponseEntity<GpuDeviceInfo> getGpuDeviceDetails(@PathVariable String deviceId) {
        log.info("Fetching GPU device details: {}", deviceId);
        
        try {
            GpuDeviceInfo device = gpuDeviceService.getGpuDeviceDetails(deviceId);
            return ResponseEntity.ok(device);
        } catch (RuntimeException e) {
            log.error("GPU device not found: {}", deviceId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error fetching GPU device details: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GPU 장비 등록
     */
    @PostMapping("/devices")
    public ResponseEntity<GpuDeviceInfo> registerGpuDevice(@RequestBody GpuDeviceRegistrationRequest request) {
        log.info("Registering GPU device: {}", request.getGpuUuid());
        
        try {
            GpuDeviceInfo device = gpuDeviceService.registerGpuDevice(request);
            return ResponseEntity.ok(device);
        } catch (RuntimeException e) {
            log.error("Error registering GPU device: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error registering GPU device: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GPU 장비 상태 업데이트
     */
    @PutMapping("/devices/{deviceId}/status")
    public ResponseEntity<Void> updateGpuDeviceStatus(
            @PathVariable String deviceId,
            @RequestParam String status) {
        log.info("Updating GPU device status: {} -> {}", deviceId, status);
        
        try {
            gpuDeviceService.updateGpuDeviceStatus(deviceId, status);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            log.error("GPU device not found: {}", deviceId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error updating GPU device status: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GPU 장비 통계 조회
     */
    @GetMapping("/devices/statistics")
    public ResponseEntity<GpuDeviceStatistics> getGpuDeviceStatistics() {
        log.info("Fetching GPU device statistics");
        
        try {
            GpuDeviceStatistics statistics = gpuDeviceService.getGpuDeviceStatistics();
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            log.error("Error fetching GPU device statistics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 과열 상태 GPU 장비 조회
     */
    @GetMapping("/devices/overheating")
    public ResponseEntity<List<GpuDeviceInfo>> getOverheatingDevices() {
        log.info("Fetching overheating GPU devices");
        
        try {
            List<GpuDeviceInfo> devices = gpuDeviceService.getOverheatingDevices();
            return ResponseEntity.ok(devices);
        } catch (Exception e) {
            log.error("Error fetching overheating devices: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ========================================================================
    // MIG 관리 API
    // ========================================================================

    /**
     * MIG 인스턴스 생성
     */
    @PostMapping("/devices/{deviceId}/mig")
    public ResponseEntity<List<MigInstanceInfo>> createMigInstances(
            @PathVariable String deviceId,
            @RequestBody List<String> profileIds) {
        log.info("Creating MIG instances for device: {} with profiles: {}", deviceId, profileIds);
        
        try {
            List<MigInstanceInfo> instances = migManagementService.createMigInstances(deviceId, profileIds);
            return ResponseEntity.ok(instances);
        } catch (RuntimeException e) {
            log.error("Error creating MIG instances: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error creating MIG instances: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * MIG 인스턴스 삭제
     */
    @DeleteMapping("/devices/{deviceId}/mig")
    public ResponseEntity<Void> deleteMigInstances(@PathVariable String deviceId) {
        log.info("Deleting MIG instances for device: {}", deviceId);
        
        try {
            migManagementService.deleteMigInstances(deviceId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            log.error("Error deleting MIG instances: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error deleting MIG instances: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 사용 가능한 MIG 인스턴스 조회
     */
    @GetMapping("/mig/available")
    public ResponseEntity<List<MigInstanceInfo>> getAvailableMigInstances() {
        log.info("Fetching available MIG instances");
        
        try {
            List<MigInstanceInfo> instances = migManagementService.getAvailableMigInstances();
            return ResponseEntity.ok(instances);
        } catch (Exception e) {
            log.error("Error fetching available MIG instances: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 특정 장비의 MIG 인스턴스 조회
     */
    @GetMapping("/devices/{deviceId}/mig")
    public ResponseEntity<List<MigInstanceInfo>> getMigInstancesByDevice(@PathVariable String deviceId) {
        log.info("Fetching MIG instances for device: {}", deviceId);
        
        try {
            List<MigInstanceInfo> instances = migManagementService.getMigInstancesByDevice(deviceId);
            return ResponseEntity.ok(instances);
        } catch (Exception e) {
            log.error("Error fetching MIG instances: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * MIG 사용률 통계 조회
     */
    @GetMapping("/mig/statistics")
    public ResponseEntity<Map<String, Object>> getMigUsageStatistics() {
        log.info("Fetching MIG usage statistics");
        
        try {
            Map<String, Object> statistics = migManagementService.getMigUsageStatistics();
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            log.error("Error fetching MIG statistics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ========================================================================
    // GPU 할당 관리 API
    // ========================================================================

    /**
     * GPU 리소스 할당
     */
    @PostMapping("/allocations")
    public ResponseEntity<GpuAllocationInfo> allocateGpuResource(@RequestBody GpuAllocationRequest request) {
        log.info("Allocating GPU resource for pod: {}/{}", request.getNamespace(), request.getPodName());
        
        try {
            GpuAllocationInfo allocation = allocationService.allocateGpuResource(request);
            return ResponseEntity.ok(allocation);
        } catch (RuntimeException e) {
            log.error("Error allocating GPU resource: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error allocating GPU resource: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GPU 리소스 해제
     */
    @DeleteMapping("/allocations/{allocationId}")
    public ResponseEntity<Void> releaseGpuResource(@PathVariable String allocationId) {
        log.info("Releasing GPU resource: {}", allocationId);
        
        try {
            allocationService.releaseGpuResource(allocationId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            log.error("Error releasing GPU resource: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error releasing GPU resource: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 활성 할당 조회
     */
    @GetMapping("/allocations")
    public ResponseEntity<List<GpuAllocationInfo>> getActiveAllocations(
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String teamId) {
        log.info("Fetching GPU allocations - namespace: {}, user: {}, team: {}", namespace, userId, teamId);
        
        try {
            List<GpuAllocationInfo> allocations;
            
            if (namespace != null) {
                allocations = allocationService.getAllocationsByNamespace(namespace);
            } else if (userId != null) {
                allocations = allocationService.getAllocationsByUser(userId);
            } else {
                allocations = allocationService.getActiveAllocations();
            }
            
            return ResponseEntity.ok(allocations);
        } catch (Exception e) {
            log.error("Error fetching GPU allocations: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 할당 비용 통계 조회
     */
    @GetMapping("/allocations/cost-statistics")
    public ResponseEntity<Map<String, Object>> getAllocationCostStatistics() {
        log.info("Fetching allocation cost statistics");
        
        try {
            Map<String, Object> statistics = allocationService.getAllocationCostStatistics();
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            log.error("Error fetching allocation cost statistics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 만료 예정 할당 조회
     */
    @GetMapping("/allocations/expiring")
    public ResponseEntity<List<GpuAllocationInfo>> getAllocationsExpiringBefore(
            @RequestParam(defaultValue = "24") int hours) {
        log.info("Fetching allocations expiring within {} hours", hours);
        
        try {
            LocalDateTime expiryTime = LocalDateTime.now().plusHours(hours);
            List<GpuAllocationInfo> allocations = allocationService.getAllocationsExpiringBefore(expiryTime);
            return ResponseEntity.ok(allocations);
        } catch (Exception e) {
            log.error("Error fetching expiring allocations: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ========================================================================
    // GPU 메트릭 및 모니터링 API
    // ========================================================================

    /**
     * GPU 사용량 통계 조회
     */
    @GetMapping("/metrics/usage-statistics")
    public ResponseEntity<Map<String, Object>> getGpuUsageStatistics(
            @RequestParam(defaultValue = "24") int hours) {
        log.info("Fetching GPU usage statistics for last {} hours", hours);
        
        try {
            Map<String, Object> statistics = metricsService.getGpuUsageStatistics(hours);
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            log.error("Error fetching GPU usage statistics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 과열 알람 조회
     */
    @GetMapping("/metrics/overheating-alerts")
    public ResponseEntity<List<Map<String, Object>>> getOverheatingAlerts() {
        log.info("Fetching overheating alerts");
        
        try {
            List<Map<String, Object>> alerts = metricsService.getOverheatingAlerts();
            return ResponseEntity.ok(alerts);
        } catch (Exception e) {
            log.error("Error fetching overheating alerts: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 수동 메트릭 수집 트리거
     */
    @PostMapping("/metrics/collect")
    public ResponseEntity<Map<String, Object>> triggerMetricsCollection() {
        log.info("Triggering manual GPU metrics collection");
        
        try {
            metricsService.collectGpuMetrics();
            
            Map<String, Object> response = Map.of(
                "status", "SUCCESS",
                "message", "GPU metrics collection triggered successfully",
                "timestamp", LocalDateTime.now()
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error triggering metrics collection: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ========================================================================
    // GPU 비용 분석 API
    // ========================================================================

    /**
     * GPU 비용 분석 조회
     */
    @GetMapping("/cost-analysis")
    public ResponseEntity<GpuCostAnalysis> getGpuCostAnalysis(
            @RequestParam(defaultValue = "30") int days) {
        log.info("Fetching GPU cost analysis for last {} days", days);
        
        try {
            GpuCostAnalysis analysis = buildCostAnalysis(days);
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            log.error("Error fetching GPU cost analysis: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 비용 최적화 제안 조회
     */
    @GetMapping("/cost-optimization")
    public ResponseEntity<List<CostOptimizationSuggestion>> getCostOptimizationSuggestions() {
        log.info("Fetching cost optimization suggestions");
        
        try {
            List<CostOptimizationSuggestion> suggestions = generateOptimizationSuggestions();
            return ResponseEntity.ok(suggestions);
        } catch (Exception e) {
            log.error("Error fetching cost optimization suggestions: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ========================================================================
    // GPU 예측 분석 API
    // ========================================================================

    /**
     * GPU 사용량 예측 조회
     */
    @GetMapping("/forecast")
    public ResponseEntity<GpuForecastAnalysis> getGpuForecast(
            @RequestParam(defaultValue = "24") int hours) {
        log.info("Fetching GPU usage forecast for next {} hours", hours);
        
        try {
            GpuForecastAnalysis forecast = buildForecastAnalysis(hours);
            return ResponseEntity.ok(forecast);
        } catch (Exception e) {
            log.error("Error fetching GPU forecast: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ========================================================================
    // GPU 헬스 체크 API
    // ========================================================================

    /**
     * GPU 클러스터 헬스 상태 조회
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getGpuClusterHealth() {
        log.info("Fetching GPU cluster health status");
        
        try {
            Map<String, Object> health = buildClusterHealth();
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            log.error("Error fetching GPU cluster health: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 특정 GPU 장비 헬스 체크
     */
    @GetMapping("/devices/{deviceId}/health")
    public ResponseEntity<Map<String, Object>> getGpuDeviceHealth(@PathVariable String deviceId) {
        log.info("Fetching health status for GPU device: {}", deviceId);
        
        try {
            Map<String, Object> health = buildDeviceHealth(deviceId);
            return ResponseEntity.ok(health);
        } catch (RuntimeException e) {
            log.error("GPU device not found: {}", deviceId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error fetching GPU device health: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ========================================================================
    // Private Helper Methods
    // ========================================================================

    private GpuClusterOverview buildClusterOverview() {
        // 실제 구현에서는 여러 서비스의 데이터를 조합
        GpuDeviceStatistics deviceStats = gpuDeviceService.getGpuDeviceStatistics();
        List<GpuAllocationInfo> activeAllocations = allocationService.getActiveAllocations();
        Map<String, Object> usageStats = metricsService.getGpuUsageStatistics(24);
        
        return GpuClusterOverview.builder()
            .totalGpuDevices(deviceStats.getTotalDevices())
            .activeAllocations(activeAllocations.size())
            .devicesByModel(deviceStats.getDevicesByModel())
            .devicesByArchitecture(deviceStats.getDevicesByArchitecture())
            .overallGpuUtilization(deviceStats.getAvgUtilization())
            .overallMemoryUtilization(calculateOverallMemoryUtilization())
            .overallTemperature(deviceStats.getAvgTemperature())
            .totalMemoryCapacityGb(deviceStats.getTotalMemoryCapacityGb().intValue())
            .availableMemoryCapacityGb(deviceStats.getAvailableMemoryCapacityGb().intValue())
            .lastUpdated(LocalDateTime.now())
            .build();
    }

    private GpuCostAnalysis buildCostAnalysis(int days) {
        // 실제 구현에서는 할당 데이터를 기반으로 비용 분석
        Map<String, Object> costStats = allocationService.getAllocationCostStatistics();
        
        return GpuCostAnalysis.builder()
            .analysisTimeRange(days + " days")
            .costByNamespace((Map<String, Double>) costStats.get("namespaceStatistics"))
            .costByTeam((Map<String, Double>) costStats.get("teamStatistics"))
            .optimizationSuggestions(generateOptimizationSuggestions())
            .analysisDate(LocalDateTime.now())
            .build();
    }

    private List<CostOptimizationSuggestion> generateOptimizationSuggestions() {
        // 실제 구현에서는 사용 패턴을 분석하여 최적화 제안 생성
        return List.of(
            CostOptimizationSuggestion.builder()
                .suggestionType("RIGHTSIZING")
                .title("MIG 인스턴스 사용 권장")
                .description("큰 메모리가 필요하지 않은 워크로드는 MIG 인스턴스를 사용하여 비용을 절약할 수 있습니다.")
                .potentialSavings(1200.0)
                .priority("HIGH")
                .implementation("워크로드 요구사항을 분석하여 적절한 MIG 프로필 선택")
                .build()
        );
    }

    private GpuForecastAnalysis buildForecastAnalysis(int hours) {
        // 실제 구현에서는 과거 데이터를 기반으로 예측 모델 적용
        return GpuForecastAnalysis.builder()
            .utilizationTrend("INCREASING")
            .costTrend("STABLE")
            .demandTrend("INCREASING")
            .forecastDate(LocalDateTime.now())
            .forecastMethod("LINEAR_REGRESSION")
            .confidence(0.85)
            .build();
    }

    private Map<String, Object> buildClusterHealth() {
        List<GpuDeviceInfo> overheatingDevices = gpuDeviceService.getOverheatingDevices();
        List<Map<String, Object>> overheatingAlerts = metricsService.getOverheatingAlerts();
        
        String overallHealth = "HEALTHY";
        if (!overheatingAlerts.isEmpty()) {
            overallHealth = overheatingAlerts.size() > 5 ? "CRITICAL" : "WARNING";
        }
        
        return Map.of(
            "overallHealth", overallHealth,
            "totalDevices", gpuDeviceService.getAllGpuDevices().size(),
            "overheatingDevices", overheatingDevices.size(),
            "activeAlerts", overheatingAlerts.size(),
            "lastHealthCheck", LocalDateTime.now()
        );
    }

    private Map<String, Object> buildDeviceHealth(String deviceId) {
        GpuDeviceInfo device = gpuDeviceService.getGpuDeviceDetails(deviceId);
        
        String healthStatus = "HEALTHY";
        List<String> issues = List.of();
        
        if (device.getCurrentTempC() != null && device.getCurrentTempC() > 85) {
            healthStatus = "WARNING";
            issues = List.of("High temperature: " + device.getCurrentTempC() + "°C");
        }
        
        return Map.of(
            "deviceId", deviceId,
            "healthStatus", healthStatus,
            "temperature", device.getCurrentTempC(),
            "powerDraw", device.getCurrentPowerW(),
            "utilization", device.getCurrentUtilization(),
            "issues", issues,
            "lastCheck", LocalDateTime.now()
        );
    }

    private Double calculateOverallMemoryUtilization() {
        // 실제 구현에서는 모든 장비의 메모리 사용률 평균 계산
        return 65.0; // 예시 값
    }
}