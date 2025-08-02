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