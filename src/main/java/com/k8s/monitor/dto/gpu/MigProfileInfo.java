package com.k8s.monitor.dto.gpu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MIG 프로필 정보 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigProfileInfo {
    
    private String profileId;
    private String modelId;
    private String profileName;
    private Integer computeSlices;
    private Integer memorySlices;
    private Integer memoryGb;
    private Integer maxInstancesPerGpu;
    private Double performanceRatio;
    private String useCase;
    private String description;
    
    // 사용량 통계
    private Integer totalInstances;
    private Integer allocatedInstances;
    private Integer availableInstances;
    private Double utilizationPercent;
}