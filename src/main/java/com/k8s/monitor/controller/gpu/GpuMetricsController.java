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
 * GPU 메트릭 관리 REST API 컨트롤러
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
     * 수동 메트릭 수집 트리거
     */
    @PostMapping("/collect")
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
}