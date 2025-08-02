package com.k8s.monitor.dto.gpu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * MIG 인스턴스 정보 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigInstanceInfo {
    
    // 기본 정보
    private String migId;
    private String deviceId;
    private String profileId;
    private String profileName;
    private Integer instanceId;
    private String migUuid;
    
    // 상태 정보
    private Boolean allocated;
    private String instanceStatus;
    private LocalDateTime createdDate;
    private LocalDateTime allocatedDate;
    private LocalDateTime lastUsedDate;
    
    // 리소스 정보
    private Integer memoryGb;
    private Integer computeSlices;
    private Integer memorySlices;
    private Double performanceRatio;
    
    // 할당 정보
    private String currentAllocationId;
    private String currentNamespace;
    private String currentPodName;
    
    // 사용량 정보
    private Double currentUtilization;
    private Double memoryUtilization;
    private Double avgUtilizationLast24h;
}