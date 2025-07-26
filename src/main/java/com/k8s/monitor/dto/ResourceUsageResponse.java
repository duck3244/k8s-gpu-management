package com.k8s.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 전체 리소스 사용량 응답 DTO
 * Pod, Node, 클러스터 요약 정보를 포함
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceUsageResponse {
    
    private List<PodResourceInfo> pods;
    private List<NodeResourceInfo> nodes;
    private ClusterSummary clusterSummary;
    private LocalDateTime timestamp;
    
    /**
     * 클러스터 전체 요약 정보
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClusterSummary {
        
        // 기본 통계
        private Integer totalNodes;
        private Integer readyNodes;
        private Integer totalPods;
        private Integer runningPods;
        private Integer vllmPods;
        private Integer sglangPods;
        
        // 전체 클러스터 용량
        private String totalCpuCapacity;
        private String totalMemoryCapacity;
        private String totalGpuCapacity;
        private String totalStorageCapacity;
        
        // 전체 할당 가능량
        private String totalCpuAllocatable;
        private String totalMemoryAllocatable;
        private String totalGpuAllocatable;
        private String totalStorageAllocatable;
        
        // 전체 사용량
        private String totalCpuUsage;
        private String totalMemoryUsage;
        private String totalGpuUsage;
        private String totalStorageUsage;
        
        // 전체 할당량 (모든 Pod Request 합계)
        private String totalCpuAllocated;
        private String totalMemoryAllocated;
        private String totalGpuAllocated;
        
        // 평균 사용률
        private Double avgCpuUsage;
        private Double avgMemoryUsage;
        private Double avgGpuUsage;
        private Double avgStorageUsage;
        
        // 평균 할당률
        private Double avgCpuAllocation;
        private Double avgMemoryAllocation;
        private Double avgGpuAllocation;
        
        // 모델별 리소스 사용량
        private ModelResourceSummary vllmResourceSummary;
        private ModelResourceSummary sglangResourceSummary;
        
        // 상위 리소스 사용 Pod/Node
        private List<String> topCpuPods;
        private List<String> topMemoryPods;
        private List<String> topGpuPods;
        
        // 알람 정보
        private List<String> alerts;
        private Integer criticalAlerts;
        private Integer warningAlerts;
    }
    
    /**
     * 모델별 리소스 요약
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelResourceSummary {
        private String modelType;
        private Integer podCount;
        private String totalCpuUsage;
        private String totalMemoryUsage;
        private String totalGpuUsage;
        private String totalCpuRequest;
        private String totalMemoryRequest;
        private String totalGpuRequest;
        private Double avgCpuUtilization;
        private Double avgMemoryUtilization;
        private Double avgGpuUtilization;
    }
}