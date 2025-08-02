package com.k8s.monitor.dto.gpu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * GPU 작업 프로필 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpuWorkloadProfile {
    
    private String profileId;
    private String workloadName;
    private String workloadType;
    
    // 리소스 요구사항
    private Integer minMemoryGb;
    private Integer preferredMemoryGb;
    private String minComputeCapability;
    private List<String> preferredArchitectures;
    private Boolean requiresMig;
    private Integer maxSharingRatio;
    
    // 성능 요구사항
    private Map<String, Object> performanceRequirements;
    private Map<String, Object> resourceConstraints;
    private String costSensitivity; // LOW, MEDIUM, HIGH
    private Map<String, Object> slaRequirements;
    
    // 사용 통계
    private Integer totalAllocations;
    private Double avgDurationHours;
    private Double avgCost;
    private Double avgUtilization;
    
    private String description;
    private String createdBy;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
}