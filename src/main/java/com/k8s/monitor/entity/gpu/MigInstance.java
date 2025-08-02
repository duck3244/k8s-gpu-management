package com.k8s.monitor.entity.gpu;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MIG 인스턴스 정보 엔티티
 */
@Entity
@Table(name = "mig_instances",
       uniqueConstraints = @UniqueConstraint(columnNames = {"device_id", "instance_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigInstance {
    
    @Id
    @Column(name = "mig_id", length = 50)
    private String migId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private GpuDevice device;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private MigProfile profile;
    
    @Column(name = "instance_id", precision = 2, nullable = false)
    private Integer instanceId; // GPU 내에서 MIG 인스턴스 ID (0-6)
    
    @Column(name = "mig_uuid", length = 100, unique = true, nullable = false)
    private String migUuid;
    
    @Column(name = "allocated", length = 1)
    private String allocated = "N"; // Y, N
    
    @Column(name = "instance_status", length = 20)
    private String instanceStatus = "ACTIVE"; // ACTIVE, INACTIVE, FAILED
    
    @Column(name = "created_date")
    private LocalDateTime createdDate;
    
    @Column(name = "allocated_date")
    private LocalDateTime allocatedDate;
    
    @Column(name = "last_used_date")
    private LocalDateTime lastUsedDate;
    
    // 관계 매핑
    @OneToMany(mappedBy = "migInstance", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<GpuAllocation> allocations;
    
    @OneToMany(mappedBy = "migInstance", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<GpuUsageMetrics> usageMetrics;
    
    @PrePersist
    protected void onCreate() {
        createdDate = LocalDateTime.now();
    }
    
    public boolean isAllocated() {
        return "Y".equals(allocated);
    }
    
    public boolean isAvailable() {
        return "N".equals(allocated) && "ACTIVE".equals(instanceStatus);
    }
}