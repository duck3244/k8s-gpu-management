package com.k8s.monitor.controller.gpu;

import com.k8s.monitor.dto.gpu.MigInstanceInfo;
import com.k8s.monitor.service.gpu.MigManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MIG 관리 REST API 컨트롤러
 * MIG 인스턴스 생성, 삭제, 조회 등의 API 제공
 */
@RestController
@RequestMapping("/api/v1/gpu/mig")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class MigManagementController {
    
    private final MigManagementService migManagementService;

    /**
     * MIG 인스턴스 생성
     */
    @PostMapping("/devices/{deviceId}")
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
    @DeleteMapping("/devices/{deviceId}")
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
    @GetMapping("/available")
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
    @GetMapping("/devices/{deviceId}")
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
    @GetMapping("/statistics")
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
}