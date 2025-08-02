package com.k8s.monitor.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Kubernetes 리소스 메트릭 기본 엔티티
 */
@Entity
@Table(name = "resource_metrics", indexes = {
    @Index(name = "idx_resource_metrics_timestamp", columnList = "timestamp"),
    @Index(name = "idx_resource_metrics_resource", columnList = "resource_type,resource_name")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceMetrics {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "resource_type", length = 20, nullable = false)
    private String resourceType; // NODE, POD, CONTAINER, GPU
    
    @Column(name = "resource_name", length = 100, nullable = false)
    private String resourceName;
    
    @Column(name = "namespace", length = 50)
    private String namespace;
    
    @Column(name = "node_name", length = 100)
    private String nodeName;
    
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    // CPU 메트릭
    @Column(name = "cpu_usage_cores", precision = 8, scale = 4)
    private Double cpuUsageCores;
    
    @Column(name = "cpu_request_cores", precision = 8, scale = 4)
    private Double cpuRequestCores;
    
    @Column(name = "cpu_limit_cores", precision = 8, scale = 4)
    private Double cpuLimitCores;
    
    // 메모리 메트릭
    @Column(name = "memory_usage_bytes", precision = 15)
    private Long memoryUsageBytes;
    
    @Column(name = "memory_request_bytes", precision = 15)
    private Long memoryRequestBytes;
    
    @Column(name = "memory_limit_bytes", precision = 15)
    private Long memoryLimitBytes;
    
    // 네트워크 메트릭
    @Column(name = "network_rx_bytes", precision = 15)
    private Long networkRxBytes;
    
    @Column(name = "network_tx_bytes", precision = 15)
    private Long networkTxBytes;
    
    // 스토리지 메트릭
    @Column(name = "storage_usage_bytes", precision = 15)
    private Long storageUsageBytes;
    
    @Column(name = "storage_available_bytes", precision = 15)
    private Long storageAvailableBytes;
    
    @Column(name = "collection_source", length = 20)
    private String collectionSource = "kubernetes-api";
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
    
    // 계산된 메트릭
    public Double getCpuUtilizationPercent() {
        if (cpuLimitCores == null || cpuLimitCores == 0 || cpuUsageCores == null) {
            return 0.0;
        }
        return (cpuUsageCores / cpuLimitCores) * 100.0;
    }
    
    public Double getMemoryUtilizationPercent() {
        if (memoryLimitBytes == null || memoryLimitBytes == 0 || memoryUsageBytes == null) {
            return 0.0;
        }
        return ((double) memoryUsageBytes / memoryLimitBytes) * 100.0;
    }
}