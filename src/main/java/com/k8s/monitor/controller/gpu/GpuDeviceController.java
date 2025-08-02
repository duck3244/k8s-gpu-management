package com.k8s.monitor.controller.gpu;

import com.k8s.monitor.dto.gpu.*;
import com.k8s.monitor.service.gpu.GpuDeviceService;
import com.k8s.monitor.service.gpu.GpuMetricsCollectionService;
import com.k8s.monitor.entity.gpu.GpuUsageMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;

/**
 * GPU 장비 관리 REST API 컨트롤러
 * GPU 장비 등록, 조회, 상태 관리 등의 API 제공
 */
@RestController
@RequestMapping("/api/v1/gpu/devices")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class GpuDeviceController {
    
    private final GpuDeviceService gpuDeviceService;
    private final GpuMetricsCollectionService metricsService; // 추가된 의존성

    /**
     * 모든 GPU 장비 조회
     */
    @GetMapping
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
    @GetMapping("/{deviceId}")
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
    @PostMapping
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
    @PutMapping("/{deviceId}/status")
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
    @GetMapping("/statistics")
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
    @GetMapping("/overheating")
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

    /**
     * 특정 GPU 장비 헬스 체크
     */
    @GetMapping("/{deviceId}/health")
    public ResponseEntity<Map<String, Object>> getGpuDeviceHealth(@PathVariable String deviceId) {
        log.info("Fetching health status for GPU device: {}", deviceId);
        
        // 입력 검증
        if (deviceId == null || deviceId.trim().isEmpty()) {
            log.warn("Invalid device ID provided: {}", deviceId);
            return ResponseEntity.badRequest().build();
        }
        
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

    /**
     * GPU 장비 헬스 상태 구성
     * 실제 구현이 포함된 메서드
     */
    private Map<String, Object> buildDeviceHealth(String deviceId) {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // 1. 기본 장비 정보 조회
            GpuDeviceInfo deviceInfo = gpuDeviceService.getGpuDeviceDetails(deviceId);
            health.put("deviceId", deviceId);
            health.put("deviceStatus", deviceInfo.getDeviceStatus());
            health.put("modelName", deviceInfo.getModelName());
            
            // 2. 최신 메트릭 정보 조회 (최근 5분)
            LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
            Map<String, Object> recentMetrics = getRecentDeviceMetrics(deviceId, fiveMinutesAgo);
            health.put("recentMetrics", recentMetrics);
            
            // 3. 온도 상태 평가 (maxTempC는 GpuDeviceInfo에 없으므로 기본값 사용)
            String temperatureStatus = evaluateTemperatureHealth(
                deviceInfo.getCurrentTempC(), 
                83.0 // 기본 최대 온도 (대부분 GPU의 일반적인 최대 온도)
            );
            health.put("temperatureStatus", temperatureStatus);
            
            // 4. 사용률 상태 평가
            String utilizationStatus = evaluateUtilizationHealth(deviceInfo.getCurrentUtilization());
            health.put("utilizationStatus", utilizationStatus);
            
            // 5. 전력 상태 평가 (PowerConsumptionW는 GpuDeviceInfo에서 직접 접근 불가하므로 수정)
            String powerStatus = evaluatePowerHealth(
                deviceInfo.getCurrentPowerW(), 
                null // 최대 전력 정보가 없으므로 null로 처리
            );
            health.put("powerStatus", powerStatus);
            
            // 6. 전반적인 헬스 상태 결정
            String overallHealth = determineOverallHealth(
                deviceInfo.getDeviceStatus(),
                temperatureStatus,
                utilizationStatus,
                powerStatus
            );
            health.put("overallHealth", overallHealth);
            
            // 7. 권장사항 생성
            List<String> recommendations = generateHealthRecommendations(
                temperatureStatus, utilizationStatus, powerStatus
            );
            health.put("recommendations", recommendations);
            
            // 8. 타임스탬프 추가
            health.put("timestamp", LocalDateTime.now());
            health.put("lastUpdateTime", deviceInfo.getLastMaintenanceDate());
            
            return health;
            
        } catch (Exception e) {
            log.error("Error building device health for {}: {}", deviceId, e.getMessage());
            
            // 오류 발생시 기본 헬스 정보 반환
            health.put("deviceId", deviceId);
            health.put("overallHealth", "ERROR");
            health.put("error", "Failed to retrieve health information");
            health.put("errorMessage", e.getMessage());
            health.put("timestamp", LocalDateTime.now());
            
            return health;
        }
    }

    /**
     * 최근 장비 메트릭 조회
     */
    private Map<String, Object> getRecentDeviceMetrics(String deviceId, LocalDateTime since) {
        try {
            // 메트릭 서비스를 통해 최근 사용률 통계 조회
            Map<String, Object> usageStats = metricsService.getGpuUsageStatistics(5); // 최근 5분
            
            Map<String, Object> deviceStats = (Map<String, Object>) 
                ((Map<String, Object>) usageStats.get("deviceStatistics")).get(deviceId);
            
            if (deviceStats != null) {
                return deviceStats;
            } else {
                // 메트릭이 없는 경우 기본값 반환
                Map<String, Object> defaultMetrics = new HashMap<>();
                defaultMetrics.put("avgGpuUtilization", 0.0);
                defaultMetrics.put("avgMemoryUtilization", 0.0);
                defaultMetrics.put("avgTemperature", 0.0);
                defaultMetrics.put("dataAvailable", false);
                return defaultMetrics;
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve recent metrics for device {}: {}", deviceId, e.getMessage());
            Map<String, Object> errorMetrics = new HashMap<>();
            errorMetrics.put("error", "Metrics unavailable");
            errorMetrics.put("dataAvailable", false);
            return errorMetrics;
        }
    }

    /**
     * 온도 헬스 상태 평가
     */
    private String evaluateTemperatureHealth(Double currentTemp, Double maxTemp) {
        if (currentTemp == null) return "UNKNOWN";
        
        if (maxTemp != null && currentTemp > maxTemp * 0.95) {
            return "CRITICAL";
        } else if (currentTemp > 85.0) {
            return "WARNING";
        } else if (currentTemp > 75.0) {
            return "CAUTION";
        } else {
            return "HEALTHY";
        }
    }

    /**
     * 사용률 헬스 상태 평가
     */
    private String evaluateUtilizationHealth(Double utilization) {
        if (utilization == null) return "UNKNOWN";
        
        if (utilization > 95.0) {
            return "HIGH_LOAD";
        } else if (utilization > 80.0) {
            return "MODERATE_LOAD";
        } else if (utilization < 5.0) {
            return "IDLE";
        } else {
            return "OPTIMAL";
        }
    }

    /**
     * 전력 헬스 상태 평가 (수정된 버전)
     */
    private String evaluatePowerHealth(Double currentPower, Integer maxPower) {
        if (currentPower == null) return "UNKNOWN";
        
        // maxPower가 없는 경우 일반적인 전력 소비 기준으로 판단
        if (maxPower != null && currentPower > maxPower * 0.9) {
            return "HIGH_CONSUMPTION";
        } else if (currentPower > 350.0) { // 일반적인 고성능 GPU 기준
            return "HIGH_CONSUMPTION";
        } else if (currentPower > 200.0) {
            return "MODERATE_CONSUMPTION";
        } else {
            return "NORMAL_CONSUMPTION";
        }
    }

    /**
     * 전반적인 헬스 상태 결정
     */
    private String determineOverallHealth(String deviceStatus, String tempStatus, 
                                        String utilizationStatus, String powerStatus) {
        
        // 장비 상태가 비활성이면 우선 반영
        if (!"ACTIVE".equals(deviceStatus) && !"MIG_ENABLED".equals(deviceStatus)) {
            return "INACTIVE";
        }
        
        // 중요도 순으로 상태 확인
        if ("CRITICAL".equals(tempStatus)) {
            return "CRITICAL";
        }
        
        if ("WARNING".equals(tempStatus) || "HIGH_CONSUMPTION".equals(powerStatus)) {
            return "WARNING";
        }
        
        if ("CAUTION".equals(tempStatus) || "HIGH_LOAD".equals(utilizationStatus)) {
            return "CAUTION";
        }
        
        return "HEALTHY";
    }

    /**
     * 헬스 권장사항 생성
     */
    private List<String> generateHealthRecommendations(String tempStatus, 
                                                     String utilizationStatus, 
                                                     String powerStatus) {
        List<String> recommendations = new ArrayList<>();
        
        if ("CRITICAL".equals(tempStatus)) {
            recommendations.add("즉시 냉각 시스템 점검 필요");
            recommendations.add("워크로드 일시 중단 고려");
        } else if ("WARNING".equals(tempStatus)) {
            recommendations.add("팬 속도 및 환기 상태 확인");
        }
        
        if ("HIGH_LOAD".equals(utilizationStatus)) {
            recommendations.add("워크로드 분산 고려");
            recommendations.add("리소스 할당 최적화 검토");
        } else if ("IDLE".equals(utilizationStatus)) {
            recommendations.add("유휴 리소스 활용 방안 검토");
        }
        
        if ("HIGH_CONSUMPTION".equals(powerStatus)) {
            recommendations.add("전력 효율성 모니터링 강화");
            recommendations.add("전력 제한 설정 검토");
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("현재 상태가 양호합니다");
        }
        
        return recommendations;
    }
}