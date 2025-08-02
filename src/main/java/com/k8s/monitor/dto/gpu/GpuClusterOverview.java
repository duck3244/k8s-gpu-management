package com.k8s.monitor.dto.gpu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * GPU 클러스터 개요 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpuClusterOverview {
    
    // 기본 통계
    private Integer totalNodes;
    private Integer totalGpuDevices;
    private Integer totalMigInstances;
    private Integer activeAllocations;
    
    // 모델별 분포
    private Map<String, Integer> devicesByModel;
    private Map<String, Integer> devicesByArchitecture;
    private Map<String, Integer> devicesByGeneration;
    
    // 사용률 정보
    private Double overallGpuUtilization;
    private Double overallMemoryUtilization;
    private Double overallTemperature;
    private Double overallPowerConsumption;
    
    // 할당 정보
    private Map<String, Integer> allocationsByWorkloadType;
    private Map<String, Integer> allocationsByNamespace;
    private Map<String, Integer> allocationsByTeam;
    
    // 비용 정보
    private Double totalHourlyCost;
    private Double totalMonthlyCost;
    private Double costByWorkloadType;
    
    // 용량 정보
    private Integer totalMemoryCapacityGb;
    private Integer availableMemoryCapacityGb;
    private Double memoryUtilizationPercent;
    
    // 알람 정보
    private Integer totalAlerts;
    private Integer criticalAlerts;
    private Integer warningAlerts;
    private List<String> topAlerts;
    
    // 성능 동향
    private Map<String, Double> utilizationTrend24h;
    private Map<String, Double> temperatureTrend24h;
    
    private LocalDateTime lastUpdated;
}