package com.k8s.monitor.dto.gpu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GPU 알람 정보 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpuAlertInfo {
    
    private String alertId;
    private String alertType; // TEMPERATURE, UTILIZATION, MEMORY, POWER, FAILURE
    private String severity; // CRITICAL, WARNING, INFO
    private String status; // ACTIVE, ACKNOWLEDGED, RESOLVED
    
    private String targetType; // DEVICE, MIG_INSTANCE, NODE
    private String targetId;
    private String targetName;
    
    private String message;
    private String description;
    private Double threshold;
    private Double currentValue;
    
    private LocalDateTime triggeredAt;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime resolvedAt;
    private String acknowledgedBy;
    private String resolvedBy;
    
    private String resolution;
    private String impact;
    private List<String> recommendedActions;
}