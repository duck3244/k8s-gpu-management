package com.k8s.monitor.dto.gpu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * GPU 장비 등록 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpuDeviceRegistrationRequest {
    
    private String nodeName;
    private String modelId;
    private Integer deviceIndex;
    private String serialNumber;
    private String pciAddress;
    private String gpuUuid;
    private String driverVersion;
    private String firmwareVersion;
    private String vbiosVersion;
    private Double purchaseCost;
    private LocalDateTime warrantyExpiryDate;
}