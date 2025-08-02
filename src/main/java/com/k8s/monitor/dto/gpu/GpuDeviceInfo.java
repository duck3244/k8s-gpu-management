package com.k8s.monitor.dto.gpu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GPU 장비 정보 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpuDeviceInfo {
    
    // 기본 정보
    private String deviceId;
    private String nodeName;
    private String modelId;
    private String modelName;
    private Integer deviceIndex;
    private String serialNumber;
    private String pciAddress;
    private String gpuUuid;
    private String deviceStatus;
    
    // 현재 상태
    private Double currentTempC;
    private Double currentPowerW;
    private Double currentUtilization;
    private Double memoryUtilization;
    
    // 하드웨어 정보
    private String driverVersion;
    private String firmwareVersion;
    private Boolean migSupport;
    private Integer memoryGb;
    private String architecture;
    
    // 관리 정보
    private LocalDateTime installationDate;
    private LocalDateTime lastMaintenanceDate;
    private LocalDateTime warrantyExpiryDate;
    private Double purchaseCost;
    
    // 할당 정보
    private Boolean allocated;
    private String currentAllocationId;
    private String currentWorkloadType;
    
    // MIG 정보 (MIG 지원 GPU의 경우)
    private List<MigInstanceInfo> migInstances;
    private Integer totalMigInstances;
    private Integer availableMigInstances;
    
    // 성능 메트릭
    private Double avgUtilizationLast24h;
    private Double avgTemperatureLast24h;
    private Double avgPowerDrawLast24h;
    
    // 알람 정보
    private List<String> activeAlerts;
    private String healthStatus; // HEALTHY, WARNING, CRITICAL
}