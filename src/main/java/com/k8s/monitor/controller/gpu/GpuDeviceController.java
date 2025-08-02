package com.k8s.monitor.controller.gpu;

import com.k8s.monitor.dto.gpu.*;
import com.k8s.monitor.service.gpu.GpuDeviceService;
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
@RequestMapping("/api/v1/gpu/devices")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class GpuDeviceController {
    
    private final GpuDeviceService gpuDeviceService;

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

    private Map<String, Object> buildDeviceHealth(String deviceId) { /* 구현 */ }
}