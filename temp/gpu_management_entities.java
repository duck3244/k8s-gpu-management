// ============================================================================
// GPU Management Entities - JPA Entity Classes
// ============================================================================

package com.k8s.monitor.entity.gpu;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GPU 모델 정보 엔티티
 */
@Entity
@Table(name = "gpu_models")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpuModel {
    
    @Id
    @Column(name = "model_id", length = 20)
    private String modelId;
    
    @Column(name = "model_name", length = 50, nullable = false)
    private String modelName;
    
    @Column(name = "manufacturer", length = 20)
    private String manufacturer = "NVIDIA";
    
    @Column(name = "architecture", length = 30, nullable = false)
    private String architecture; // Pascal, Turing, Ampere, Hopper, Ada Lovelace
    
    @Column(name = "memory_gb", precision = 3)
    private Integer memoryGb;
    
    @Column(name = "cuda_cores", precision = 6)
    private Integer cudaCores;
    
    @Column(name = "tensor_cores", precision = 4)
    private Integer tensorCores;
    
    @Column(name = "rt_cores", precision = 4)
    private Integer rtCores = 0;
    
    @Column(name = "base_clock_mhz", precision = 5)
    private Integer baseClockMhz;
    
    @Column(name = "boost_clock_mhz", precision = 5)
    private Integer boostClockMhz;
    
    @Column(name = "memory_bandwidth_gbps", precision = 6, scale = 1)
    private Double memoryBandwidthGbps;
    
    @Column(name = "memory_type", length = 20)
    private String memoryType; // GDDR6, GDDR6X, HBM2, HBM3
    
    @Column(name = "power_consumption_w", precision = 4, nullable = false)
    private Integer powerConsumptionW;
    
    @Column(name = "pcie_generation", length = 10)
    private String pcieGeneration;
    
    @Column(name = "mig_support", length = 1)
    private String migSupport = "N"; // Y, N
    
    @Column(name = "max_mig_instances", precision = 2)
    private Integer maxMigInstances = 0;
    
    @Column(name = "compute_capability", length = 10)
    private String computeCapability; // 6.1, 7.5, 8.0, 9.0
    
    @Column(name = "release_year", precision = 4)
    private Integer releaseYear;
    
    @Column(name = "market_segment", length = 20)
    private String marketSegment; // Gaming, Professional, Datacenter
    
    @Column(name = "end_of_life_date")
    private LocalDateTime endOfLifeDate;
    
    @Column(name = "created_date")
    private LocalDateTime createdDate;
    
    @Column(name = "updated_date")
    private LocalDateTime updatedDate;
    
    // 관계 매핑
    @OneToMany(mappedBy = "model", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<GpuDevice> devices;
    
    @OneToMany(mappedBy = "model", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<MigProfile> migProfiles;
    
    @PrePersist
    protected void onCreate() {
        createdDate = LocalDateTime.now();
        updatedDate = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedDate = LocalDateTime.now();
    }
    
    public boolean supportsMig() {
        return "Y".equals(migSupport);
    }
}

/**
 * Kubernetes 노드 정보 엔티티 (기존 확장)
 */
@Entity
@Table(name = "gpu_nodes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpuNode {
    
    @Id
    @Column(name = "node_id", length = 50)
    private String nodeId;
    
    @Column(name = "node_name", length = 100, nullable = false)
    private String nodeName;
    
    @Column(name = "cluster_name", length = 50)
    private String clusterName;
    
    @Column(name = "node_ip", length = 15)
    private String nodeIp;
    
    @Column(name = "total_gpus", precision = 3)
    private Integer totalGpus = 0;
    
    @Column(name = "available_gpus", precision = 3)
    private Integer availableGpus = 0;
    
    @Column(name = "node_status", length = 20)
    private String nodeStatus = "ACTIVE"; // ACTIVE, INACTIVE, MAINTENANCE, FAILED
    
    @Column(name = "kubernetes_version", length = 20)
    private String kubernetesVersion;
    
    @Column(name = "docker_version", length = 20)
    private String dockerVersion;
    
    @Column(name = "nvidia_driver_version", length = 20)
    private String nvidiaDriverVersion;
    
    @Lob
    @Column(name = "node_labels")
    private String nodeLabels; // JSON format
    
    @Lob
    @Column(name = "taints")
    private String taints; // JSON format
    
    @Column(name = "created_date")
    private LocalDateTime createdDate;
    
    @Column(name = "updated_date")
    private LocalDateTime updatedDate;
    
    // 관계 매핑
    @OneToMany(mappedBy = "node", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<GpuDevice> gpuDevices;
    
    @PrePersist
    protected void onCreate() {
        createdDate = LocalDateTime.now();
        updatedDate = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedDate = LocalDateTime.now();
    }
}

/**
 * GPU 장비 정보 엔티티
 */
@Entity
@Table(name = "gpu_devices", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"node_id", "device_index"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpuDevice {
    
    @Id
    @Column(name = "device_id", length = 50)
    private String deviceId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id", nullable = false)
    private GpuNode node;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_id", nullable = false)
    private GpuModel model;
    
    @Column(name = "device_index", precision = 2, nullable = false)
    private Integer deviceIndex; // GPU index on node (0, 1, 2, ...)
    
    @Column(name = "serial_number", length = 50)
    private String serialNumber;
    
    @Column(name = "pci_address", length = 20, nullable = false)
    private String pciAddress;
    
    @Column(name = "gpu_uuid", length = 100, unique = true, nullable = false)
    private String gpuUuid;
    
    @Column(name = "device_status", length = 20)
    private String deviceStatus = "ACTIVE"; // ACTIVE, INACTIVE, MAINTENANCE, FAILED, MIG_ENABLED
    
    @Column(name = "current_temp_c", precision = 5, scale = 2)
    private Double currentTempC;
    
    @Column(name = "max_temp_c", precision = 5, scale = 2)
    private Double maxTempC = 83.0;
    
    @Column(name = "current_power_w", precision = 6, scale = 2)
    private Double currentPowerW;
    
    @Column(name = "max_power_w", precision = 6, scale = 2)
    private Double maxPowerW;
    
    @Column(name = "driver_version", length = 20)
    private String driverVersion;
    
    @Column(name = "firmware_version", length = 20)
    private String firmwareVersion;
    
    @Column(name = "vbios_version", length = 20)
    private String vbiosVersion;
    
    @Column(name = "installation_date")
    private LocalDateTime installationDate;
    
    @Column(name = "last_maintenance_date")
    private LocalDateTime lastMaintenanceDate;
    
    @Column(name = "warranty_expiry_date")
    private LocalDateTime warrantyExpiryDate;
    
    @Column(name = "purchase_cost", precision = 10, scale = 2)
    private Double purchaseCost;
    
    @Column(name = "depreciation_months", precision = 3)
    private Integer depreciationMonths = 36;
    
    @Column(name = "created_date")
    private LocalDateTime createdDate;
    
    @Column(name = "updated_date")
    private LocalDateTime updatedDate;
    
    // 관계 매핑
    @OneToMany(mappedBy = "device", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<MigInstance> migInstances;
    
    @OneToMany(mappedBy = "device", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<GpuUsageMetrics> usageMetrics;
    
    @PrePersist
    protected void onCreate() {
        createdDate = LocalDateTime.now();
        updatedDate = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedDate = LocalDateTime.now();
    }
    
    public boolean isMigEnabled() {
        return "MIG_ENABLED".equals(deviceStatus);
    }
    
    public boolean isAvailable() {
        return "ACTIVE".equals(deviceStatus);
    }
}

/**
 * MIG 프로필 정보 엔티티
 */
@Entity
@Table(name = "mig_profiles",
       uniqueConstraints = @UniqueConstraint(columnNames = {"model_id", "profile_name"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigProfile {
    
    @Id
    @Column(name = "profile_id", length = 20)
    private String profileId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_id", nullable = false)
    private GpuModel model;
    
    @Column(name = "profile_name", length = 50, nullable = false)
    private String profileName; // 1g.5gb, 2g.10gb, 3g.20gb, 4g.20gb, 7g.40gb
    
    @Column(name = "compute_slices", precision = 2, nullable = false)
    private Integer computeSlices;
    
    @Column(name = "memory_slices", precision = 2, nullable = false)
    private Integer memorySlices;
    
    @Column(name = "memory_gb", precision = 3, nullable = false)
    private Integer memoryGb;
    
    @Column(name = "max_instances_per_gpu", precision = 2, nullable = false)
    private Integer maxInstancesPerGpu;
    
    @Column(name = "performance_ratio", precision = 4, scale = 2)
    private Double performanceRatio; // Relative performance compared to full GPU
    
    @Column(name = "use_case", length = 100)
    private String useCase; // Training, Inference, Development
    
    @Column(name = "description", length = 200)
    private String description;
    
    @Column(name = "created_date")
    private LocalDateTime createdDate;
    
    // 관계 매핑
    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<MigInstance> migInstances;
    
    @PrePersist
    protected void onCreate() {
        createdDate = LocalDateTime.now();
    }
}

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

/**
 * GPU 사용량 메트릭 엔티티 (기존 ResourceMetrics 확장)
 */
@Entity
@Table(name = "gpu_usage_metrics", indexes = {
    @Index(name = "idx_gpu_metrics_device_time", columnList = "device_id,timestamp"),
    @Index(name = "idx_gpu_metrics_mig_time", columnList = "mig_id,timestamp"),
    @Index(name = "idx_gpu_metrics_timestamp", columnList = "timestamp")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpuUsageMetrics {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
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