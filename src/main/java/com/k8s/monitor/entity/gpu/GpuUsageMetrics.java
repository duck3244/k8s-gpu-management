package com.k8s.monitor.entity.gpu;

import com.k8s.monitor.entity.ResourceMetrics;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * GPU 사용량 메트릭 엔티티 - ResourceMetrics 확장
 */
@Entity
@Table(name = "gpu_usage_metrics", indexes = {
    @Index(name = "idx_gpu_metrics_device_time", columnList = "device_id,timestamp"),
    @Index(name = "idx_gpu_metrics_mig_time", columnList = "mig_id,timestamp"),
    @Index(name = "idx_gpu_metrics_timestamp", columnList = "timestamp"),
    @Index(name = "idx_gpu_metrics_resource", columnList = "base_metrics_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpuUsageMetrics {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // 기본 리소스 메트릭과의 연관관계
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "base_metrics_id")
    private ResourceMetrics baseMetrics;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private GpuDevice device;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mig_id")
    private MigInstance migInstance;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allocation_id")
    private GpuAllocation allocation;
    
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    // GPU 전용 메트릭
    @Column(name = "gpu_utilization_pct", precision = 5, scale = 2)
    private Double gpuUtilizationPct;
    
    @Column(name = "memory_used_mb", precision = 8)
    private Long memoryUsedMb;
    
    @Column(name = "memory_total_mb", precision = 8)
    private Long memoryTotalMb;
    
    @Column(name = "memory_utilization_pct", precision = 5, scale = 2)
    private Double memoryUtilizationPct;
    
    @Column(name = "temperature_c", precision = 5, scale = 2)
    private Double temperatureC;
    
    @Column(name = "power_draw_w", precision = 6, scale = 2)
    private Double powerDrawW;
    
    @Column(name = "fan_speed_pct", precision = 5, scale = 2)
    private Double fanSpeedPct;
    
    @Column(name = "clock_graphics_mhz", precision = 5)
    private Integer clockGraphicsMhz;
    
    @Column(name = "clock_memory_mhz", precision = 5)
    private Integer clockMemoryMhz;
    
    @Column(name = "pcie_tx_mbps", precision = 8, scale = 2)
    private Double pcieTxMbps;
    
    @Column(name = "pcie_rx_mbps", precision = 8, scale = 2)
    private Double pcieRxMbps;
    
    @Column(name = "processes_count", precision = 3)
    private Integer processesCount;
    
    @Column(name = "collection_source", length = 20)
    private String collectionSource = "nvidia-smi";
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        
        // 기본 메트릭 정보 자동 생성
        if (baseMetrics == null) {
            baseMetrics = ResourceMetrics.builder()
                .resourceType("GPU")
                .resourceName(device != null ? device.getDeviceId() : 
                            (migInstance != null ? migInstance.getMigId() : "unknown"))
                .nodeName(device != null ? device.getNode().getNodeName() : null)
                .timestamp(timestamp)
                .collectionSource(collectionSource)
                .build();
        }
    }
    
    public Double getMemoryUtilizationRatio() {
        if (memoryTotalMb == null || memoryTotalMb == 0 || memoryUsedMb == null) {
            return 0.0;
        }
        return (double) memoryUsedMb / memoryTotalMb;
    }
    
    public boolean isOverheating() {
        return temperatureC != null && temperatureC > 85.0;
    }
    
    public boolean isHighUtilization() {
        return gpuUtilizationPct != null && gpuUtilizationPct > 90.0;
    }
}