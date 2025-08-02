package com.k8s.monitor.dto.gpu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * GPU 장비 통계 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpuDeviceStatistics {
    
    private Integer totalDevices;
    private Integer activeDevices;
    private Integer migEnabledDevices;
    private Integer maintenanceDevices;
    private Integer failedDevices;
    
    private Map<String, Integer> devicesByNode;
    private Map<String, Integer> devicesByModel;
    private Map<String, Integer> devicesByArchitecture;
    
    private Double totalMemoryCapacityGb;
    private Double availableMemoryCapacityGb;
    private Double memoryUtilizationPercent;
    
    private Double avgUtilization;
    private Double avgTemperature;
    private Double avgPowerConsumption;
    
    private LocalDateTime statisticsTime;
}