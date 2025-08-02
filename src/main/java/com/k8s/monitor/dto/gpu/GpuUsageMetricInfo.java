package com.k8s.monitor.dto.gpu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * GPU 사용량 메트릭 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpuUsageMetricInfo {
    
    private String deviceId;
    private String migId;
    private String allocationId;
    private LocalDateTime timestamp;
    
    // GPU 사용률
    private Double gpuUtilizationPct;
    private Double memoryUtilizationPct;
    private Long memoryUsedMb;
    private Long memoryTotalMb;
    
    // 하드웨어 상태
    private Double temperatureC;
    private Double powerDrawW;
    private Double fanSpeedPct;
    
    // 클럭 정보
    private Integer clockGraphicsMhz;
    private Integer clockMemoryMhz;
    
    // 네트워크 정보
    private Double pcieTxMbps;
    private Double pcieRxMbps;
    
    // 프로세스 정보
    private Integer processesCount;
    private String collectionSource;
    
    // 계산된 필드
    private String utilizationLevel; // LOW, MEDIUM, HIGH, CRITICAL
    private String temperatureLevel; // NORMAL, WARNING, CRITICAL
    private Boolean isOverheating;
    private Boolean isHighUtilization;
}