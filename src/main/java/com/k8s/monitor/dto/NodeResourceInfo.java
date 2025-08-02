package com.k8s.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Node 리소스 정보를 담는 DTO
 * 클러스터 노드의 전체 용량, 할당 가능량, 현재 사용량 정보
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeResourceInfo {
    
    // 기본 정보
    private String name;
    private String role; // master, worker
    private String status; // Ready, NotReady
    private LocalDateTime lastUpdateTime;
    
    // 노드 전체 용량 (Capacity)
    private String cpuCapacity;
    private String memoryCapacity;
    private String gpuCapacity;
    private String storageCapacity;
    
    // 할당 가능한 리소스 (Allocatable)
    private String cpuAllocatable;
    private String memoryAllocatable;
    private String gpuAllocatable;
    private String storageAllocatable;
    
    // 현재 사용량
    private String cpuUsage;
    private String memoryUsage;
    private String gpuUsage;
    private String storageUsage;
    
    // 사용률 (%)
    private Double cpuUsagePercent;
    private Double memoryUsagePercent;
    private Double gpuUsagePercent;
    private Double storageUsagePercent;
    
    // 할당된 리소스 (모든 Pod의 Request 합계)
    private String cpuAllocated;
    private String memoryAllocated;
    private String gpuAllocated;
    
    // 할당률 (%)
    private Double cpuAllocationPercent;
    private Double memoryAllocationPercent;
    private Double gpuAllocationPercent;
    
    // 메타데이터
    private Map<String, String> labels;
    private Map<String, String> annotations;
    
    // Pod 정보
    private Integer totalPodCount;
    private Integer runningPodCount;
    private Integer vllmPodCount;
    private Integer sglangPodCount;
    
    // 노드 상세 정보
    private String kubeletVersion;
    private String containerRuntimeVersion;
    private String operatingSystem;
    private String architecture;
}