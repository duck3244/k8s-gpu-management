package com.k8s.monitor.dto.gpu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * GPU 할당 정보 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpuAllocationInfo {
    
    // 할당 기본 정보
    private String allocationId;
    private String namespace;
    private String podName;
    private String containerName;
    private String workloadType;
    
    // 리소스 정보
    private String resourceType; // FULL_GPU, MIG_INSTANCE, SHARED_GPU
    private String allocatedResource; // device_id or mig_id
    private Integer requestedMemoryGb;
    private Integer allocatedMemoryGb;
    private String priorityClass;
    
    // 시간 정보
    private LocalDateTime allocationTime;
    private LocalDateTime plannedReleaseTime;
    private LocalDateTime releaseTime;
    private String status;
    private Long usageDurationHours;
    
    // 비용 정보
    private Double costPerHour;
    private Double totalCost;
    private Double estimatedMonthlyCost;
    
    // 사용자 정보
    private String userId;
    private String teamId;
    private String projectId;
    
    // 추가 정보
    private GpuDeviceInfo deviceInfo;
    private MigInstanceInfo migInstanceInfo;
    private Map<String, String> labels;
    private Map<String, String> annotations;
}