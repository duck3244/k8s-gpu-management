package com.k8s.monitor.dto.gpu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * GPU 할당 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpuAllocationRequest {
    
    // Pod 정보
    private String namespace;
    private String podName;
    private String containerName;
    
    // 워크로드 정보
    private String workloadType; // Training, Inference, Development, Gaming
    private String priorityClass;
    
    // 리소스 요구사항
    private Boolean useMig;
    private Integer requiredMemoryGb;
    private String preferredModelId;
    private String preferredArchitecture;
    
    // 스케줄링 정보
    private LocalDateTime plannedReleaseTime;
    private Integer maxDurationHours;
    
    // 사용자 정보
    private String userId;
    private String teamId;
    private String projectId;
    
    // 비용 관리
    private Double maxCostPerHour;
    private Double maxTotalCost;
    
    // 선호도 설정
    private Boolean preferHighMemory;
    private Boolean preferNewGeneration;
    private Boolean allowSharedGpu;
}