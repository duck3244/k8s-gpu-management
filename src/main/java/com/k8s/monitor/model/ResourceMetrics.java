package com.k8s.monitor.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 리소스 메트릭 저장을 위한 JPA Entity
 * Pod과 Node의 시계열 리소스 사용량 데이터 저장
 */
@Entity
@Table(name = "resource_metrics", indexes = {
    @Index(name = "idx_resource_timestamp", columnList = "timestamp"),
    @Index(name = "idx_resource_type_name", columnList = "resourceType,resourceName"),
    @Index(name = "idx_namespace_timestamp", columnList = "namespace,timestamp")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceMetrics {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // 리소스 식별 정보
    @Column(nullable = false, length = 20)
    private String resourceType; // POD, NODE
    
    @Column(nullable = false, length = 255)
    private String resourceName;
    
    @Column(length = 100)
    private String namespace;
    
    @Column(length = 255)
    private String nodeName;
    
    // 시간 정보
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    // CPU 메트릭
    @Column(length = 50)
    private String cpuUsage;
    
    @Column(length = 50)
    private String cpuRequest;
    
    @Column(length = 50)
    private String cpuLimit;
    
    private Double cpuUsagePercent;
    
    // 메모리 메트릭
    @Column(length = 50)
    private String memoryUsage;
    
    @Column(length = 50)
    private String memoryRequest;
    
    @Column(length = 50)
    private String memoryLimit;
    
    private Double memoryUsagePercent;
    
    // GPU 메트릭
    @Column(length = 50)
    private String gpuUsage;
    
    @Column(length = 50)
    private String gpuRequest;
    
    @Column(length = 50)
    private String gpuLimit;
    
    private Double gpuUsagePercent;
    
    // 스토리지 메트릭
    @Column(length = 50)
    private String storageUsage;
    
    @Column(length = 50)
    private String storageRequest;
    
    @Column(length = 50)
    private String storageLimit;
    
    private Double storageUsagePercent;
    
    // 상태 정보
    @Column(length = 50)
    private String status;
    
    @Column(length = 50)
    private String phase;
    
    // 모델 서빙 관련 정보
    @Column(length = 50)
    private String modelType; // vllm, sglang
    
    @Column(length = 100)
    private String modelName;
    
    @Column(length = 50)
    private String modelVersion;
    
    // 네트워크 메트릭 (선택적)
    private Long networkInBytes;
    private Long networkOutBytes;
    
    // 추가 메타데이터
    @Column(length = 1000)
    private String labels; // JSON 형태로 저장
    
    @Column(length = 2000)
    private String annotations; // JSON 형태로 저장
    
    // 생성/수정 시간
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}