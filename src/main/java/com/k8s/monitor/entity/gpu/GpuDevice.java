package com.k8s.monitor.entity.gpu;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

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

    // getter/setter
    public String getVbiosVersion() {
        return vbiosVersion;
    }
    
    public void setVbiosVersion(String vbiosVersion) {
        this.vbiosVersion = vbiosVersion;
    }
    
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