package com.k8s.monitor.entity.gpu;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

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