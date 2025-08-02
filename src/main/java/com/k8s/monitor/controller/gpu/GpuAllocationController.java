package com.k8s.monitor.controller.gpu;

import com.k8s.monitor.dto.gpu.GpuAllocationInfo;
import com.k8s.monitor.dto.gpu.GpuAllocationRequest;
import com.k8s.monitor.service.gpu.GpuAllocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * GPU 할당 관리 REST API 컨트롤러
 * GPU 리소스 할당, 해제, 조회 등의 API 제공
 */
@RestController
@RequestMapping("/api/v1/gpu/allocations")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class GpuAllocationController {
    
    private final GpuAllocationService allocationService;

    /**
     * GPU 리소스 할당
     */
    @PostMapping
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
    @DeleteMapping("/{allocationId}")
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
    @GetMapping
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
    @GetMapping("/cost-statistics")
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
    @GetMapping("/expiring")
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
}