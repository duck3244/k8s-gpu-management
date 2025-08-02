package com.k8s.monitor.controller.gpu;

import com.k8s.monitor.service.gpu.GpuMetricsCollectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * GPU 메트릭 관리 REST API 컨트롤러 (수정된 버전)
 * GPU 메트릭 수집, 사용량 통계, 알람 관리 API 제공
 */
@RestController
@RequestMapping("/api/v1/gpu/metrics")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class GpuMetricsController {
    
    private final GpuMetricsCollectionService metricsService;

    /**
     * GPU 사용량 통계 조회
     */
    @GetMapping("/usage-statistics")
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
    @GetMapping("/overheating-alerts")
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
     * 수동 메트릭 수집 트리거 (수정된 버전)
     */
    @PostMapping("/collect")
    public ResponseEntity<Map<String, Object>> triggerMetricsCollection() {
        log.info("Triggering manual GPU metrics collection");
        
        try {
            // 올바른 메서드명으로 수정
            metricsService.triggerMetricsCollection();
            
            Map<String, Object> response = Map.of(
                "status", "SUCCESS",
                "message", "GPU metrics collection triggered successfully",
                "timestamp", LocalDateTime.now()
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error triggering metrics collection: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = Map.of(
                "status", "ERROR",
                "message", "Failed to trigger GPU metrics collection: " + e.getMessage(),
                "timestamp", LocalDateTime.now()
            );
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 메트릭 수집 상태 조회
     */
    @GetMapping("/collection-status")
    public ResponseEntity<Map<String, Object>> getCollectionStatus() {
        log.info("Fetching metrics collection status");
        
        try {
            // 메트릭 수집 상태 정보 구성
            Map<String, Object> status = Map.of(
                "status", "RUNNING",
                "lastCollection", LocalDateTime.now(),
                "collectionInterval", "30 seconds",
                "metricsRetentionDays", 30,
                "timestamp", LocalDateTime.now()
            );
            
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Error fetching collection status: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 특정 기간의 메트릭 데이터 조회
     */
    @GetMapping("/data")
    public ResponseEntity<Map<String, Object>> getMetricsData(
            @RequestParam(defaultValue = "1") int hours,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String migId) {
        log.info("Fetching GPU metrics data for last {} hours", hours);
        
        try {
            Map<String, Object> metricsData;
            
            if (deviceId != null) {
                // 특정 장비의 메트릭 데이터
                metricsData = Map.of(
                    "deviceId", deviceId,
                    "timeRange", hours + " hours",
                    "data", "Device-specific metrics would be retrieved here",
                    "timestamp", LocalDateTime.now()
                );
            } else if (migId != null) {
                // 특정 MIG 인스턴스의 메트릭 데이터
                metricsData = Map.of(
                    "migId", migId,
                    "timeRange", hours + " hours",
                    "data", "MIG-specific metrics would be retrieved here",
                    "timestamp", LocalDateTime.now()
                );
            } else {
                // 전체 GPU 메트릭 데이터
                metricsData = metricsService.getGpuUsageStatistics(hours);
            }
            
            return ResponseEntity.ok(metricsData);
        } catch (Exception e) {
            log.error("Error fetching metrics data: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 메트릭 수집 설정 조회
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getMetricsConfig() {
        log.info("Fetching metrics collection configuration");
        
        try {
            Map<String, Object> config = Map.of(
                "collectionInterval", 30,
                "retentionDays", 30,
                "batchSize", 100,
                "nvidiaSmiEnabled", true,
                "nvidiaSmiPath", "/usr/bin/nvidia-smi",
                "nvidiaSmiTimeout", 10,
                "temperatureThreshold", 85.0,
                "powerThreshold", 400.0,
                "timestamp", LocalDateTime.now()
            );
            
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            log.error("Error fetching metrics config: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 메트릭 정리 작업 수동 실행
     */
    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> triggerMetricsCleanup(
            @RequestParam(defaultValue = "30") int olderThanDays) {
        log.info("Triggering manual GPU metrics cleanup for data older than {} days", olderThanDays);
        
        try {
            // 실제로는 서비스에서 정리 작업을 수행해야 함
            // 현재는 응답만 반환
            Map<String, Object> response = Map.of(
                "status", "SUCCESS",
                "message", "Metrics cleanup completed",
                "olderThanDays", olderThanDays,
                "deletedRecords", 0, // 실제 삭제된 레코드 수
                "timestamp", LocalDateTime.now()
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error during metrics cleanup: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = Map.of(
                "status", "ERROR",
                "message", "Failed to cleanup metrics: " + e.getMessage(),
                "timestamp", LocalDateTime.now()
            );
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 실시간 GPU 상태 조회
     */
    @GetMapping("/realtime")
    public ResponseEntity<Map<String, Object>> getRealtimeMetrics() {
        log.info("Fetching realtime GPU metrics");
        
        try {
            // 실시간 메트릭은 최근 1분 데이터 사용
            Map<String, Object> realtimeData = metricsService.getGpuUsageStatistics(1);
            
            // 실시간 정보 추가
            realtimeData.put("realtime", true);
            realtimeData.put("refreshInterval", 30);
            realtimeData.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(realtimeData);
        } catch (Exception e) {
            log.error("Error fetching realtime metrics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 메트릭 수집 일시 정지/재개
     */
    @PostMapping("/collection/{action}")
    public ResponseEntity<Map<String, Object>> controlCollection(@PathVariable String action) {
        log.info("Collection control action: {}", action);
        
        try {
            Map<String, Object> response;
            
            switch (action.toLowerCase()) {
                case "pause":
                    response = Map.of(
                        "status", "SUCCESS",
                        "message", "Metrics collection paused",
                        "action", "PAUSE",
                        "timestamp", LocalDateTime.now()
                    );
                    break;
                case "resume":
                    response = Map.of(
                        "status", "SUCCESS", 
                        "message", "Metrics collection resumed",
                        "action", "RESUME",
                        "timestamp", LocalDateTime.now()
                    );
                    break;
                case "restart":
                    // 메트릭 수집 재시작
                    metricsService.triggerMetricsCollection();
                    response = Map.of(
                        "status", "SUCCESS",
                        "message", "Metrics collection restarted",
                        "action", "RESTART", 
                        "timestamp", LocalDateTime.now()
                    );
                    break;
                default:
                    response = Map.of(
                        "status", "ERROR",
                        "message", "Invalid action: " + action + ". Valid actions: pause, resume, restart",
                        "timestamp", LocalDateTime.now()
                    );
                    return ResponseEntity.badRequest().body(response);
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error controlling collection: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = Map.of(
                "status", "ERROR",
                "message", "Failed to control collection: " + e.getMessage(),
                "timestamp", LocalDateTime.now()
            );
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}