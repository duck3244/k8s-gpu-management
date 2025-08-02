package com.k8s.monitor.entity.gpu;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * GPU 할당 정보 엔티티
 */
@Entity
@Table(name = "gpu_allocations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpuAllocation {
    
    @Id
    @Column(name = "allocation_id", length = 50)
    private String allocationId;
    
    @Column(name = "namespace", length = 50, nullable = false)
    private String namespace;
    
    @Column(name = "pod_name", length = 100, nullable = false)
    private String podName;
    
    @Column(name = "container_name", length = 100)
    private String containerName;
    
    @Column(name = "workload_type", length = 30)
    private String workloadType; // Training, Inference, Development, Gaming
    
    @Column(name = "resource_type", length = 20, nullable = false)
    private String resourceType; // FULL_GPU, MIG_INSTANCE, SHARED_GPU
    
    @Column(name = "allocated_resource", length = 50, nullable = false)
    private String allocatedResource; // device_id or mig_id
    
    @Column(name = "requested_memory_gb", precision = 3)
    private Integer requestedMemoryGb;
    
    @Column(name = "allocated_memory_gb", precision = 3)
    private Integer allocatedMemoryGb;
    
    @Column(name = "priority_class", length = 20)
    private String priorityClass = "normal";
    
    @Column(name = "allocation_time")
    private LocalDateTime allocationTime;
    
    @Column(name = "planned_release_time")
    private LocalDateTime plannedReleaseTime;
    
    @Column(name = "release_time")
    private LocalDateTime releaseTime;
    
    @Column(name = "status", length = 20)
    private String status = "ALLOCATED"; // PENDING, ALLOCATED, RELEASED, FAILED, EXPIRED
    
    @Column(name = "cost_per_hour", precision = 8, scale = 4)
    private Double costPerHour;
    
    @Column(name = "total_cost", precision = 10, scale = 2)
    private Double totalCost;
    
    @Column(name = "user_id", length = 50)
    private String userId;
    
    @Column(name = "team_id", length = 50)
    private String teamId;
    
    @Column(name = "project_id", length = 50)
    private String projectId;
    
    @Column(name = "created_date")
    private LocalDateTime createdDate;
    
    @Column(name = "updated_date")
    private LocalDateTime updatedDate;
    
    // 관계 매핑
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", referencedColumnName = "device_id")
    private GpuDevice device;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mig_id", referencedColumnName = "mig_id")
    private MigInstance migInstance;
    
    @PrePersist
    protected void onCreate() {
        createdDate = LocalDateTime.now();
        updatedDate = LocalDateTime.now();
        if (allocationTime == null) {
            allocationTime = LocalDateTime.now();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedDate = LocalDateTime.now();
    }
    
    public boolean isActive() {
        return "ALLOCATED".equals(status);
    }
    
    public boolean isExpired() {
        return plannedReleaseTime != null && LocalDateTime.now().isAfter(plannedReleaseTime);
    }
}