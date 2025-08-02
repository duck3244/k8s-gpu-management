package com.k8s.monitor.controller.gpu;

import com.k8s.monitor.dto.gpu.*;
import com.k8s.monitor.service.gpu.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GPU 클러스터 메인 관리 REST API 컨트롤러
 * 클러스터 개요, 헬스체크, 예측 분석 API 제공
 */
@RestController
@RequestMapping("/api/v1/gpu")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class GpuManagementController {
    
    private final GpuDeviceService gpuDeviceService;
    private final GpuAllocationService allocationService;
    private final GpuMetricsCollectionService metricsService;
    private final GpuForecastService forecastService; // 추가된 의존성
    private final GpuCostAnalysisService costAnalysisService; // 추가된 의존성

    /**
     * GPU 클러스터 전체 개요 조회
     */
    @GetMapping("/overview")
    public ResponseEntity<GpuClusterOverview> getClusterOverview() {
        log.info("Fetching GPU cluster overview");
        
        try {
            GpuClusterOverview overview = buildClusterOverview();
            return ResponseEntity.ok(overview);
        } catch (Exception e) {
            log.error("Error fetching GPU cluster overview: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GPU 사용량 예측 조회
     */
    @GetMapping("/forecast")
    public ResponseEntity<GpuForecastAnalysis> getGpuForecast(
            @RequestParam(defaultValue = "24") int hours) {
        log.info("Fetching GPU usage forecast for next {} hours", hours);
        
        // 입력 검증
        if (hours <= 0 || hours > 8760) { // 최대 1년
            log.warn("Invalid forecast hours: {}", hours);
            return ResponseEntity.badRequest().build();
        }
        
        try {
            GpuForecastAnalysis forecast = buildForecastAnalysis(hours);
            return ResponseEntity.ok(forecast);
        } catch (Exception e) {
            log.error("Error fetching GPU forecast: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

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

    // Helper methods 구현

    /**
     * GPU 클러스터 개요 구성
     */
    private GpuClusterOverview buildClusterOverview() {
        try {
            log.debug("Building GPU cluster overview");
            
            // 1. 기본 통계 수집
            GpuDeviceStatistics deviceStats = gpuDeviceService.getGpuDeviceStatistics();
            List<GpuAllocationInfo> activeAllocations = allocationService.getActiveAllocations();
            
            // 2. 장비별 분포 계산
            Map<String, Integer> devicesByModel = deviceStats.getDevicesByModel();
            Map<String, Integer> devicesByNode = deviceStats.getDevicesByNode();
            
            // 아키텍처별 분포 (모델명에서 추출)
            Map<String, Integer> devicesByArchitecture = calculateDevicesByArchitecture(devicesByModel);
            
            // 세대별 분포 (모델명에서 추출)
            Map<String, Integer> devicesByGeneration = calculateDevicesByGeneration(devicesByModel);
            
            // 3. 사용률 정보 계산
            Map<String, Object> utilizationInfo = calculateUtilizationInfo();
            
            // 4. 할당 정보 분석
            Map<String, Integer> allocationsByWorkloadType = calculateAllocationsByWorkloadType(activeAllocations);
            Map<String, Integer> allocationsByNamespace = calculateAllocationsByNamespace(activeAllocations);
            Map<String, Integer> allocationsByTeam = calculateAllocationsByTeam(activeAllocations);
            
            // 5. 비용 정보 계산
            Map<String, Double> costInfo = calculateCostInfo(activeAllocations);
            
            // 6. 용량 정보 계산
            Map<String, Integer> capacityInfo = calculateCapacityInfo(deviceStats);
            
            // 7. 알람 정보 수집
            Map<String, Object> alertInfo = calculateAlertInfo();
            
            // 8. 성능 동향 계산
            Map<String, Double> utilizationTrend24h = calculateUtilizationTrend();
            Map<String, Double> temperatureTrend24h = calculateTemperatureTrend();
            
            return GpuClusterOverview.builder()
                // 기본 통계
                .totalNodes(devicesByNode.size())
                .totalGpuDevices(deviceStats.getTotalDevices())
                .totalMigInstances(0) // MIG 통계는 별도 서비스에서 조회 필요
                .activeAllocations(activeAllocations.size())
                
                // 분포 정보
                .devicesByModel(devicesByModel)
                .devicesByArchitecture(devicesByArchitecture)
                .devicesByGeneration(devicesByGeneration)
                
                // 사용률 정보
                .overallGpuUtilization((Double) utilizationInfo.get("gpuUtilization"))
                .overallMemoryUtilization((Double) utilizationInfo.get("memoryUtilization"))
                .overallTemperature((Double) utilizationInfo.get("temperature"))
                .overallPowerConsumption((Double) utilizationInfo.get("powerConsumption"))
                
                // 할당 정보
                .allocationsByWorkloadType(allocationsByWorkloadType)
                .allocationsByNamespace(allocationsByNamespace)
                .allocationsByTeam(allocationsByTeam)
                
                // 비용 정보
                .totalHourlyCost(costInfo.get("hourlyCost"))
                .totalMonthlyCost(costInfo.get("monthlyCost"))
                .costByWorkloadType(costInfo.get("workloadCost"))
                
                // 용량 정보
                .totalMemoryCapacityGb(capacityInfo.get("totalMemory"))
                .availableMemoryCapacityGb(capacityInfo.get("availableMemory"))
                .memoryUtilizationPercent(calculateMemoryUtilizationPercent(capacityInfo))
                
                // 알람 정보
                .totalAlerts((Integer) alertInfo.get("total"))
                .criticalAlerts((Integer) alertInfo.get("critical"))
                .warningAlerts((Integer) alertInfo.get("warning"))
                .topAlerts((List<String>) alertInfo.get("topAlerts"))
                
                // 성능 동향
                .utilizationTrend24h(utilizationTrend24h)
                .temperatureTrend24h(temperatureTrend24h)
                
                .lastUpdated(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Error building cluster overview: {}", e.getMessage(), e);
            // 기본값으로 빈 개요 반환
            return createDefaultClusterOverview();
        }
    }

    /**
     * GPU 예측 분석 구성
     */
    private GpuForecastAnalysis buildForecastAnalysis(int hours) {
        try {
            log.debug("Building GPU forecast analysis for {} hours", hours);
            
            // GpuForecastService가 있다면 해당 서비스 사용
            if (forecastService != null) {
                return forecastService.generateUsageForecast(hours);
            }
            
            // 서비스가 없는 경우 기본 예측 분석 생성
            return createBasicForecastAnalysis(hours);
            
        } catch (Exception e) {
            log.error("Error building forecast analysis: {}", e.getMessage(), e);
            return createDefaultForecastAnalysis(hours);
        }
    }

    /**
     * GPU 클러스터 헬스 상태 구성
     */
    private Map<String, Object> buildClusterHealth() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            log.debug("Building GPU cluster health status");
            
            // 1. 전체 장비 상태 확인
            GpuDeviceStatistics deviceStats = gpuDeviceService.getGpuDeviceStatistics();
            Map<String, Object> deviceHealth = calculateDeviceHealth(deviceStats);
            health.put("deviceHealth", deviceHealth);
            
            // 2. 할당 상태 확인
            List<GpuAllocationInfo> activeAllocations = allocationService.getActiveAllocations();
            Map<String, Object> allocationHealth = calculateAllocationHealth(activeAllocations);
            health.put("allocationHealth", allocationHealth);
            
            // 3. 메트릭 수집 상태 확인
            Map<String, Object> metricsHealth = calculateMetricsHealth();
            health.put("metricsHealth", metricsHealth);
            
            // 4. 리소스 사용률 확인
            Map<String, Object> resourceHealth = calculateResourceHealth();
            health.put("resourceHealth", resourceHealth);
            
            // 5. 알람 상태 확인
            List<Map<String, Object>> activeAlerts = metricsService.getOverheatingAlerts();
            Map<String, Object> alertHealth = calculateAlertHealth(activeAlerts);
            health.put("alertHealth", alertHealth);
            
            // 6. 전체 헬스 상태 결정
            String overallStatus = determineOverallHealthStatus(
                deviceHealth, allocationHealth, metricsHealth, resourceHealth, alertHealth
            );
            health.put("overallStatus", overallStatus);
            
            // 7. 헬스 점수 계산 (0-100)
            Integer healthScore = calculateHealthScore(
                deviceHealth, allocationHealth, metricsHealth, resourceHealth
            );
            health.put("healthScore", healthScore);
            
            // 8. 권장사항 생성
            List<String> recommendations = generateClusterRecommendations(
                overallStatus, deviceHealth, allocationHealth, activeAlerts
            );
            health.put("recommendations", recommendations);
            
            // 9. 메타 정보
            health.put("timestamp", LocalDateTime.now());
            health.put("lastCheck", LocalDateTime.now());
            health.put("checksPerformed", 5);
            
            return health;
            
        } catch (Exception e) {
            log.error("Error building cluster health: {}", e.getMessage(), e);
            return createErrorHealthStatus(e);
        }
    }

    // Private helper methods

    private Map<String, Integer> calculateDevicesByArchitecture(Map<String, Integer> devicesByModel) {
        Map<String, Integer> result = new HashMap<>();
        
        for (Map.Entry<String, Integer> entry : devicesByModel.entrySet()) {
            String model = entry.getKey();
            Integer count = entry.getValue();
            
            String architecture = extractArchitecture(model);
            result.merge(architecture, count, Integer::sum);
        }
        
        return result;
    }

    private Map<String, Integer> calculateDevicesByGeneration(Map<String, Integer> devicesByModel) {
        Map<String, Integer> result = new HashMap<>();
        
        for (Map.Entry<String, Integer> entry : devicesByModel.entrySet()) {
            String model = entry.getKey();
            Integer count = entry.getValue();
            
            String generation = extractGeneration(model);
            result.merge(generation, count, Integer::sum);
        }
        
        return result;
    }

    private String extractArchitecture(String modelName) {
        if (modelName.contains("RTX40") || modelName.contains("RTX 40")) return "Ada Lovelace";
        if (modelName.contains("RTX30") || modelName.contains("RTX 30")) return "Ampere";
        if (modelName.contains("RTX20") || modelName.contains("RTX 20")) return "Turing";
        if (modelName.contains("GTX10") || modelName.contains("GTX 10")) return "Pascal";
        if (modelName.contains("A100") || modelName.contains("H100")) return "Hopper";
        if (modelName.contains("V100")) return "Volta";
        return "Unknown";
    }

    private String extractGeneration(String modelName) {
        if (modelName.contains("RTX40") || modelName.contains("H100")) return "Latest";
        if (modelName.contains("RTX30") || modelName.contains("A100")) return "Current";
        if (modelName.contains("RTX20") || modelName.contains("V100")) return "Previous";
        if (modelName.contains("GTX10") || modelName.contains("GTX16")) return "Legacy";
        return "Unknown";
    }

    private Map<String, Object> calculateUtilizationInfo() {
        Map<String, Object> info = new HashMap<>();
        
        try {
            Map<String, Object> stats = metricsService.getGpuUsageStatistics(1); // 최근 1시간
            Map<String, Object> deviceStats = (Map<String, Object>) stats.get("deviceStatistics");
            
            if (deviceStats != null && !deviceStats.isEmpty()) {
                double totalGpuUtil = 0.0, totalMemoryUtil = 0.0, totalTemp = 0.0, totalPower = 0.0;
                int count = 0;
                
                for (Object deviceData : deviceStats.values()) {
                    if (deviceData instanceof Map) {
                        Map<String, Object> data = (Map<String, Object>) deviceData;
                        totalGpuUtil += getDoubleValue(data, "avgGpuUtilization");
                        totalMemoryUtil += getDoubleValue(data, "avgMemoryUtilization");
                        totalTemp += getDoubleValue(data, "avgTemperature");
                        count++;
                    }
                }
                
                if (count > 0) {
                    info.put("gpuUtilization", totalGpuUtil / count);
                    info.put("memoryUtilization", totalMemoryUtil / count);
                    info.put("temperature", totalTemp / count);
                    info.put("powerConsumption", totalPower / count);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to calculate utilization info: {}", e.getMessage());
        }
        
        // 기본값 설정
        info.putIfAbsent("gpuUtilization", 0.0);
        info.putIfAbsent("memoryUtilization", 0.0);
        info.putIfAbsent("temperature", 0.0);
        info.putIfAbsent("powerConsumption", 0.0);
        
        return info;
    }

    private double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }

    private Map<String, Integer> calculateAllocationsByWorkloadType(List<GpuAllocationInfo> allocations) {
        Map<String, Integer> result = new HashMap<>();
        
        for (GpuAllocationInfo allocation : allocations) {
            String workloadType = allocation.getWorkloadType() != null ? 
                allocation.getWorkloadType() : "Unknown";
            result.merge(workloadType, 1, Integer::sum);
        }
        
        return result;
    }

    private Map<String, Integer> calculateAllocationsByNamespace(List<GpuAllocationInfo> allocations) {
        Map<String, Integer> result = new HashMap<>();
        
        for (GpuAllocationInfo allocation : allocations) {
            String namespace = allocation.getNamespace() != null ? 
                allocation.getNamespace() : "Unknown";
            result.merge(namespace, 1, Integer::sum);
        }
        
        return result;
    }

    private Map<String, Integer> calculateAllocationsByTeam(List<GpuAllocationInfo> allocations) {
        Map<String, Integer> result = new HashMap<>();
        
        for (GpuAllocationInfo allocation : allocations) {
            String teamId = allocation.getTeamId() != null ? 
                allocation.getTeamId() : "Unknown";
            result.merge(teamId, 1, Integer::sum);
        }
        
        return result;
    }

    private Map<String, Double> calculateCostInfo(List<GpuAllocationInfo> allocations) {
        Map<String, Double> result = new HashMap<>();
        
        double totalHourlyCost = allocations.stream()
            .filter(allocation -> allocation.getCostPerHour() != null)
            .mapToDouble(GpuAllocationInfo::getCostPerHour)
            .sum();
        
        result.put("hourlyCost", totalHourlyCost);
        result.put("monthlyCost", totalHourlyCost * 24 * 30);
        result.put("workloadCost", totalHourlyCost); // 간단히 전체 비용으로 설정
        
        return result;
    }

    private Map<String, Integer> calculateCapacityInfo(GpuDeviceStatistics deviceStats) {
        Map<String, Integer> result = new HashMap<>();
        
        // 기본값 또는 실제 계산 로직
        result.put("totalMemory", deviceStats.getTotalDevices() * 24); // GPU당 평균 24GB 가정
        result.put("availableMemory", (deviceStats.getTotalDevices() - 
            (deviceStats.getTotalDevices() - (deviceStats.getActiveDevices() != null ? deviceStats.getActiveDevices() : 0))) * 24);
        
        return result;
    }

    private Double calculateMemoryUtilizationPercent(Map<String, Integer> capacityInfo) {
        Integer total = capacityInfo.get("totalMemory");
        Integer available = capacityInfo.get("availableMemory");
        
        if (total != null && total > 0 && available != null) {
            return ((double) (total - available) / total) * 100.0;
        }
        
        return 0.0;
    }

    private Map<String, Object> calculateAlertInfo() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            List<Map<String, Object>> alerts = metricsService.getOverheatingAlerts();
            
            int total = alerts.size();
            int critical = (int) alerts.stream()
                .filter(alert -> "CRITICAL".equals(alert.get("severity")))
                .count();
            int warning = total - critical;
            
            List<String> topAlerts = alerts.stream()
                .limit(5)
                .map(alert -> (String) alert.get("deviceId") + ": " + alert.get("severity"))
                .collect(java.util.stream.Collectors.toList());
            
            result.put("total", total);
            result.put("critical", critical);
            result.put("warning", warning);
            result.put("topAlerts", topAlerts);
            
        } catch (Exception e) {
            log.warn("Failed to calculate alert info: {}", e.getMessage());
            result.put("total", 0);
            result.put("critical", 0);
            result.put("warning", 0);
            result.put("topAlerts", new ArrayList<>());
        }
        
        return result;
    }

    private Map<String, Double> calculateUtilizationTrend() {
        // 간단한 더미 데이터 - 실제로는 시계열 데이터 분석 필요
        Map<String, Double> trend = new HashMap<>();
        for (int i = 0; i < 24; i++) {
            trend.put("hour_" + i, Math.random() * 100);
        }
        return trend;
    }

    private Map<String, Double> calculateTemperatureTrend() {
        // 간단한 더미 데이터 - 실제로는 시계열 데이터 분석 필요
        Map<String, Double> trend = new HashMap<>();
        for (int i = 0; i < 24; i++) {
            trend.put("hour_" + i, 65.0 + Math.random() * 20);
        }
        return trend;
    }

    // 기본값 생성 메서드들

    private GpuClusterOverview createDefaultClusterOverview() {
        return GpuClusterOverview.builder()
            .totalNodes(0)
            .totalGpuDevices(0)
            .totalMigInstances(0)
            .activeAllocations(0)
            .devicesByModel(new HashMap<>())
            .devicesByArchitecture(new HashMap<>())
            .devicesByGeneration(new HashMap<>())
            .overallGpuUtilization(0.0)
            .overallMemoryUtilization(0.0)
            .overallTemperature(0.0)
            .overallPowerConsumption(0.0)
            .allocationsByWorkloadType(new HashMap<>())
            .allocationsByNamespace(new HashMap<>())
            .allocationsByTeam(new HashMap<>())
            .totalHourlyCost(0.0)
            .totalMonthlyCost(0.0)
            .costByWorkloadType(0.0)
            .totalMemoryCapacityGb(0)
            .availableMemoryCapacityGb(0)
            .memoryUtilizationPercent(0.0)
            .totalAlerts(0)
            .criticalAlerts(0)
            .warningAlerts(0)
            .topAlerts(new ArrayList<>())
            .utilizationTrend24h(new HashMap<>())
            .temperatureTrend24h(new HashMap<>())
            .lastUpdated(LocalDateTime.now())
            .build();
    }

    private GpuForecastAnalysis createBasicForecastAnalysis(int hours) {
        return GpuForecastAnalysis.builder()
            .utilizationForecast24h(new HashMap<>())
            .utilizationForecast7d(new HashMap<>())
            .utilizationForecast30d(new HashMap<>())
            .costForecast24h(0.0)
            .costForecast7d(0.0)
            .costForecast30d(0.0)
            .utilizationTrend("STABLE")
            .costTrend("STABLE")
            .demandTrend("STABLE")
            .scalingRecommendations(new ArrayList<>())
            .optimizationRecommendations(new ArrayList<>())
            .forecastDate(LocalDateTime.now())
            .forecastMethod("BASIC")
            .confidence(0.5)
            .build();
    }

    private GpuForecastAnalysis createDefaultForecastAnalysis(int hours) {
        return createBasicForecastAnalysis(hours);
    }

    // 헬스 체크 관련 메서드들

    private Map<String, Object> calculateDeviceHealth(GpuDeviceStatistics deviceStats) {
        Map<String, Object> health = new HashMap<>();
        
        int total = deviceStats.getTotalDevices();
        int active = deviceStats.getActiveDevices() != null ? deviceStats.getActiveDevices() : 0;
        int failed = deviceStats.getFailedDevices() != null ? deviceStats.getFailedDevices() : 0;
        
        double healthRatio = total > 0 ? (double) active / total : 0.0;
        
        health.put("totalDevices", total);
        health.put("activeDevices", active);
        health.put("failedDevices", failed);
        health.put("healthRatio", healthRatio);
        health.put("status", healthRatio > 0.9 ? "HEALTHY" : healthRatio > 0.7 ? "WARNING" : "CRITICAL");
        
        return health;
    }

    private Map<String, Object> calculateAllocationHealth(List<GpuAllocationInfo> allocations) {
        Map<String, Object> health = new HashMap<>();
        
        long totalAllocations = allocations.size();
        long healthyAllocations = allocations.stream()
            .filter(allocation -> "ALLOCATED".equals(allocation.getStatus()))
            .count();
        
        double healthRatio = totalAllocations > 0 ? (double) healthyAllocations / totalAllocations : 1.0;
        
        health.put("totalAllocations", totalAllocations);
        health.put("healthyAllocations", healthyAllocations);
        health.put("healthRatio", healthRatio);
        health.put("status", healthRatio > 0.95 ? "HEALTHY" : healthRatio > 0.8 ? "WARNING" : "CRITICAL");
        
        return health;
    }

    private Map<String, Object> calculateMetricsHealth() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            Map<String, Object> stats = metricsService.getGpuUsageStatistics(1);
            boolean metricsAvailable = stats != null && !stats.isEmpty();
            
            health.put("available", metricsAvailable);
            health.put("lastCollection", LocalDateTime.now());
            health.put("status", metricsAvailable ? "HEALTHY" : "CRITICAL");
            
        } catch (Exception e) {
            health.put("available", false);
            health.put("error", e.getMessage());
            health.put("status", "CRITICAL");
        }
        
        return health;
    }

    private Map<String, Object> calculateResourceHealth() {
        Map<String, Object> health = new HashMap<>();
        
        // 간단한 리소스 헬스 체크
        Map<String, Object> utilizationInfo = calculateUtilizationInfo();
        
        double gpuUtilization = (Double) utilizationInfo.get("gpuUtilization");
        double temperature = (Double) utilizationInfo.get("temperature");
        
        String status = "HEALTHY";
        if (temperature > 85.0 || gpuUtilization > 95.0) {
            status = "CRITICAL";
        } else if (temperature > 80.0 || gpuUtilization > 90.0) {
            status = "WARNING";
        }
        
        health.put("gpuUtilization", gpuUtilization);
        health.put("temperature", temperature);
        health.put("status", status);
        
        return health;
    }

    private Map<String, Object> calculateAlertHealth(List<Map<String, Object>> alerts) {
        Map<String, Object> health = new HashMap<>();
        
        int total = alerts.size();
        long critical = alerts.stream()
            .filter(alert -> "CRITICAL".equals(alert.get("severity")))
            .count();
        
        String status = "HEALTHY";
        if (critical > 0) {
            status = "CRITICAL";
        } else if (total > 5) {
            status = "WARNING";
        }
        
        health.put("totalAlerts", total);
        health.put("criticalAlerts", critical);
        health.put("status", status);
        
        return health;
    }

    private String determineOverallHealthStatus(Map<String, Object>... healthMaps) {
        for (Map<String, Object> healthMap : healthMaps) {
            String status = (String) healthMap.get("status");
            if ("CRITICAL".equals(status)) {
                return "CRITICAL";
            }
        }
        
        for (Map<String, Object> healthMap : healthMaps) {
            String status = (String) healthMap.get("status");
            if ("WARNING".equals(status)) {
                return "WARNING";
            }
        }
        
        return "HEALTHY";
    }

    private Integer calculateHealthScore(Map<String, Object>... healthMaps) {
        int totalScore = 0;
        int count = 0;
        
        for (Map<String, Object> healthMap : healthMaps) {
            String status = (String) healthMap.get("status");
            switch (status) {
                case "HEALTHY":
                    totalScore += 100;
                    break;
                case "WARNING":
                    totalScore += 70;
                    break;
                case "CRITICAL":
                    totalScore += 30;
                    break;
                default:
                    totalScore += 50;
            }
            count++;
        }
        
        return count > 0 ? totalScore / count : 50;
    }

    private List<String> generateClusterRecommendations(String overallStatus, 
                                                       Map<String, Object> deviceHealth,
                                                       Map<String, Object> allocationHealth,
                                                       List<Map<String, Object>> alerts) {
        List<String> recommendations = new ArrayList<>();
        
        if ("CRITICAL".equals(overallStatus)) {
            recommendations.add("클러스터 상태가 위험합니다. 즉시 점검이 필요합니다.");
        }
        
        if ("CRITICAL".equals(deviceHealth.get("status"))) {
            recommendations.add("실패한 GPU 장비가 있습니다. 하드웨어 점검을 수행하세요.");
        }
        
        if (!alerts.isEmpty()) {
            recommendations.add("활성 알람이 " + alerts.size() + "개 있습니다. 알람을 확인하세요.");
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("클러스터 상태가 양호합니다.");
        }
        
        return recommendations;
    }

    private Map<String, Object> createErrorHealthStatus(Exception e) {
        Map<String, Object> errorHealth = new HashMap<>();
        errorHealth.put("overallStatus", "ERROR");
        errorHealth.put("error", e.getMessage());
        errorHealth.put("timestamp", LocalDateTime.now());
        errorHealth.put("healthScore", 0);
        errorHealth.put("recommendations", List.of("시스템 오류가 발생했습니다. 관리자에게 문의하세요."));
        
        // 기본 헬스 구조 유지
        Map<String, Object> defaultHealth = new HashMap<>();
        defaultHealth.put("status", "ERROR");
        
        errorHealth.put("deviceHealth", defaultHealth);
        errorHealth.put("allocationHealth", defaultHealth);
        errorHealth.put("metricsHealth", defaultHealth);
        errorHealth.put("resourceHealth", defaultHealth);
        errorHealth.put("alertHealth", defaultHealth);
        
        return errorHealth;
    }
}