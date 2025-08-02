package com.k8s.monitor.entity.gpu;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

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