package com.k8s.monitor.service.gpu;

import com.k8s.monitor.dto.gpu.*;
import com.k8s.monitor.entity.gpu.*;
import com.k8s.monitor.repository.gpu.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * GPU 할당 관리 서비스
 * GPU 리소스 할당, 해제, 모니터링 등의 기능 제공
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GpuAllocationService {
    
    private final GpuAllocationRepository allocationRepository;
    private final GpuDeviceRepository gpuDeviceRepository;
    private final MigInstanceRepository migInstanceRepository;
    private final GpuCostAnalysisService costAnalysisService;

    /**
     * GPU 리소스 할당
     */
    @Transactional
    public GpuAllocationInfo allocateGpuResource(GpuAllocationRequest request) {
        log.info("Allocating GPU resource for pod: {}/{}", request.getNamespace(), request.getPodName());
        
        // 할당할 리소스 찾기
        String allocatedResource;
        String resourceType;
        
        if (Boolean.TRUE.equals(request.getUseMig())) {
            // MIG 인스턴스 할당
            MigInstance migInstance = findSuitableMigInstance(request);
            if (migInstance == null) {
                throw new RuntimeException("No suitable MIG instance available for allocation");
            }
            
            allocatedResource = migInstance.getMigId();
            resourceType = "MIG_INSTANCE";
            
            // MIG 인스턴스를 할당됨으로 표시
            migInstance.setAllocated("Y");
            migInstance.setAllocatedDate(LocalDateTime.now());
            migInstanceRepository.save(migInstance);
            
        } else {
            // 전체 GPU 할당
            GpuDevice device = findSuitableGpuDevice(request);
            if (device == null) {
                throw new RuntimeException("No suitable GPU device available for allocation");
            }
            
            allocatedResource = device.getDeviceId();
            resourceType = "FULL_GPU";
        }
        
        // 비용 계산
        Double costPerHour = costAnalysisService.calculateCostPerHour(resourceType, allocatedResource);
        
        // 할당 정보 생성
        GpuAllocation allocation = GpuAllocation.builder()
            .allocationId(generateAllocationId())
            .namespace(request.getNamespace())
            .podName(request.getPodName())
            .containerName(request.getContainerName())
            .workloadType(request.getWorkloadType())
            .resourceType(resourceType)
            .allocatedResource(allocatedResource)
            .requestedMemoryGb(request.getRequiredMemoryGb())
            .allocatedMemoryGb(getAllocatedMemoryGb(resourceType, allocatedResource))
            .priorityClass(request.getPriorityClass())
            .allocationTime(LocalDateTime.now())
            .plannedReleaseTime(request.getPlannedReleaseTime())
            .status("ALLOCATED")
            .costPerHour(costPerHour)
            .userId(request.getUserId())
            .teamId(request.getTeamId())
            .projectId(request.getProjectId())
            .build();
        
        allocation = allocationRepository.save(allocation);
        
        log.info("GPU resource allocated: {} -> {}", allocatedResource, allocation.getAllocationId());
        return convertToAllocationDto(allocation);
    }

    /**
     * GPU 리소스 해제
     */
    @Transactional
    public void releaseGpuResource(String allocationId) {
        GpuAllocation allocation = allocationRepository.findById(allocationId)
            .orElseThrow(() -> new RuntimeException("Allocation not found: " + allocationId));
        
        if (!"ALLOCATED".equals(allocation.getStatus())) {
            throw new RuntimeException("Allocation is not in allocated status: " + allocation.getStatus());
        }
        
        // 사용 시간 계산 및 비용 업데이트
        LocalDateTime releaseTime = LocalDateTime.now();
        long usageHours = java.time.Duration.between(allocation.getAllocationTime(), releaseTime).toHours();
        Double totalCost = allocation.getCostPerHour() * Math.max(1, usageHours); // 최소 1시간 과금
        
        allocation.setReleaseTime(releaseTime);
        allocation.setStatus("RELEASED");
        allocation.setTotalCost(totalCost);
        allocationRepository.save(allocation);
        
        // 리소스 해제
        if ("MIG_INSTANCE".equals(allocation.getResourceType())) {
            MigInstance migInstance = migInstanceRepository.findById(allocation.getAllocatedResource())
                .orElseThrow(() -> new RuntimeException("MIG instance not found: " + allocation.getAllocatedResource()));
            migInstance.setAllocated("N");
            migInstance.setLastUsedDate(releaseTime);
            migInstanceRepository.save(migInstance);
        }
        
        log.info("GPU resource released: {} (used {} hours, cost: ${})", 
                allocation.getAllocatedResource(), usageHours, totalCost);
    }

    /**
     * 활성 할당 조회
     */
    public List<GpuAllocationInfo> getActiveAllocations() {
        return allocationRepository.findActiveAllocations().stream()
            .map(this::convertToAllocationDto)
            .collect(Collectors.toList());
    }

    /**
     * 네임스페이스별 할당 조회
     */
    public List<GpuAllocationInfo> getAllocationsByNamespace(String namespace) {
        return allocationRepository.findByNamespace(namespace).stream()
            .map(this::convertToAllocationDto)
            .collect(Collectors.toList());
    }

    /**
     * 사용자별 할당 조회
     */
    public List<GpuAllocationInfo> getAllocationsByUser(String userId) {
        return allocationRepository.findByUserId(userId).stream()
            .map(this::convertToAllocationDto)
            .collect(Collectors.toList());
    }

    /**
     * 만료 예정 할당 조회
     */
    public List<GpuAllocationInfo> getAllocationsExpiringBefore(LocalDateTime expiryTime) {
        return allocationRepository.findAllocationsExpiringBefore(expiryTime).stream()
            .map(this::convertToAllocationDto)
            .collect(Collectors.toList());
    }

    /**
     * 할당 비용 통계 조회
     */
    public Map<String, Object> getAllocationCostStatistics() {
        List<Object[]> costStatsByNamespace = allocationRepository.findCostStatsByNamespace();
        List<Object[]> costStatsByTeam = allocationRepository.findCostStatsByTeam();
        List<Object[]> usageStatsByWorkload = allocationRepository.findUsageStatsByWorkload();
        
        Map<String, Object> namespaceStats = costStatsByNamespace.stream()
            .collect(Collectors.toMap(
                row -> (String) row[0],
                row -> Map.of(
                    "count", ((Number) row[1]).intValue(),
                    "totalCost", ((Number) row[2]).doubleValue(),
                    "avgCost", ((Number) row[3]).doubleValue()
                )
            ));
        
        Map<String, Object> teamStats = costStatsByTeam.stream()
            .collect(Collectors.toMap(
                row -> (String) row[0],
                row -> Map.of(
                    "count", ((Number) row[1]).intValue(),
                    "totalCost", ((Number) row[2]).doubleValue(),
                    "avgCost", ((Number) row[3]).doubleValue()
                )
            ));
        
        Map<String, Object> workloadStats = usageStatsByWorkload.stream()
            .collect(Collectors.toMap(
                row -> (String) row[0],
                row -> Map.of(
                    "count", ((Number) row[1]).intValue(),
                    "avgDurationHours", ((Number) row[2]).doubleValue()
                )
            ));
        
        return Map.of(
            "namespaceStatistics", namespaceStats,
            "teamStatistics", teamStats,
            "workloadStatistics", workloadStats,
            "lastUpdated", LocalDateTime.now()
        );
    }

    /**
     * 만료된 할당 자동 해제 (스케줄러)
     */
    @Scheduled(fixedRate = 300000) // 5분마다 실행
    @Transactional
    public void autoExpireAllocations() {
        int expiredCount = allocationRepository.expireOldAllocations();
        if (expiredCount > 0) {
            log.info("Auto-expired {} allocations", expiredCount);
            
            // 만료된 할당의 MIG 인스턴스 해제
            List<GpuAllocation> expiredAllocations = allocationRepository.findByStatus("EXPIRED");
            for (GpuAllocation allocation : expiredAllocations) {
                if ("MIG_INSTANCE".equals(allocation.getResourceType())) {
                    migInstanceRepository.findById(allocation.getAllocatedResource())
                        .ifPresent(migInstance -> {
                            migInstance.setAllocated("N");
                            migInstance.setLastUsedDate(LocalDateTime.now());
                            migInstanceRepository.save(migInstance);
                        });
                }
            }
        }
    }

    // Private helper methods
    
    private MigInstance findSuitableMigInstance(GpuAllocationRequest request) {
        List<MigInstance> availableInstances = migInstanceRepository.findAvailableInstances();
        
        return availableInstances.stream()
            .filter(instance -> {
                // 메모리 요구사항 확인
                if (request.getRequiredMemoryGb() != null && 
                    instance.getProfile().getMemoryGb() < request.getRequiredMemoryGb()) {
                    return false;
                }
                
                // 선호 모델 확인
                if (request.getPreferredModelId() != null && 
                    !request.getPreferredModelId().equals(instance.getDevice().getModel().getModelId())) {
                    return false;
                }
                
                return true;
            })
            .findFirst()
            .orElse(null);
    }

    private GpuDevice findSuitableGpuDevice(GpuAllocationRequest request) {
        List<GpuDevice> availableDevices = gpuDeviceRepository.findAvailableDevices();
        
        return availableDevices.stream()
            .filter(device -> {
                // 메모리 요구사항 확인
                if (request.getRequiredMemoryGb() != null && 
                    device.getModel().getMemoryGb() < request.getRequiredMemoryGb()) {
                    return false;
                }
                
                // 선호 모델 확인
                if (request.getPreferredModelId() != null && 
                    !request.getPreferredModelId().equals(device.getModel().getModelId())) {
                    return false;
                }
                
                // 선호 아키텍처 확인
                if (request.getPreferredArchitecture() != null && 
                    !request.getPreferredArchitecture().equals(device.getModel().getArchitecture())) {
                    return false;
                }
                
                return true;
            })
            .findFirst()
            .orElse(null);
    }

    private Integer getAllocatedMemoryGb(String resourceType, String resourceId) {
        if ("MIG_INSTANCE".equals(resourceType)) {
            return migInstanceRepository.findById(resourceId)
                .map(instance -> instance.getProfile().getMemoryGb())
                .orElse(0);
        } else {
            return gpuDeviceRepository.findById(resourceId)
                .map(device -> device.getModel().getMemoryGb())
                .orElse(0);
        }
    }

    private GpuAllocationInfo convertToAllocationDto(GpuAllocation allocation) {
        return GpuAllocationInfo.builder()
            .allocationId(allocation.getAllocationId())
            .namespace(allocation.getNamespace())
            .podName(allocation.getPodName())
            .containerName(allocation.getContainerName())
            .workloadType(allocation.getWorkloadType())
            .resourceType(allocation.getResourceType())
            .allocatedResource(allocation.getAllocatedResource())
            .requestedMemoryGb(allocation.getRequestedMemoryGb())
            .allocatedMemoryGb(allocation.getAllocatedMemoryGb())
            .priorityClass(allocation.getPriorityClass())
            .allocationTime(allocation.getAllocationTime())
            .plannedReleaseTime(allocation.getPlannedReleaseTime())
            .releaseTime(allocation.getReleaseTime())
            .status(allocation.getStatus())
            .costPerHour(allocation.getCostPerHour())
            .totalCost(allocation.getTotalCost())
            .userId(allocation.getUserId())
            .teamId(allocation.getTeamId())
            .projectId(allocation.getProjectId())
            .build();
    }

    private String generateAllocationId() {
        return "ALLOC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}