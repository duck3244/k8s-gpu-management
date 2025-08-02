package com.k8s.monitor.repository.gpu;

import com.k8s.monitor.entity.gpu.GpuNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * GPU 노드 정보 Repository
 */
@Repository
public interface GpuNodeRepository extends JpaRepository<GpuNode, String> {
    
    // 노드명으로 조회
    Optional<GpuNode> findByNodeName(String nodeName);
    
    // 클러스터별 조회
    List<GpuNode> findByClusterName(String clusterName);
    
    // 노드 상태별 조회
    List<GpuNode> findByNodeStatus(String nodeStatus);
    
    // GPU 보유 노드 조회
    @Query("SELECT n FROM GpuNode n WHERE n.totalGpus > 0")
    List<GpuNode> findNodesWithGpus();
    
    // 사용 가능한 GPU가 있는 노드 조회
    @Query("SELECT n FROM GpuNode n WHERE n.availableGpus > 0 AND n.nodeStatus = 'ACTIVE'")
    List<GpuNode> findNodesWithAvailableGpus();
    
    // 클러스터 GPU 요약 통계
    @Query("SELECT n.clusterName, SUM(n.totalGpus), SUM(n.availableGpus) " +
           "FROM GpuNode n GROUP BY n.clusterName")
    List<Object[]> findClusterGpuSummary();
    
    // NVIDIA 드라이버 버전별 조회
    List<GpuNode> findByNvidiaDriverVersion(String driverVersion);
    
    // Kubernetes 버전별 조회
    List<GpuNode> findByKubernetesVersion(String kubernetesVersion);
    
    // 활성 노드 조회
    @Query("SELECT n FROM GpuNode n WHERE n.nodeStatus = 'ACTIVE'")
    List<GpuNode> findActiveNodes();
    
    // GPU 사용률이 높은 노드 조회
    @Query("SELECT n FROM GpuNode n WHERE n.totalGpus > 0 AND " +
           "(CAST(n.totalGpus - n.availableGpus AS DOUBLE) / n.totalGpus) > :utilizationThreshold")
    List<GpuNode> findHighUtilizationNodes(@Param("utilizationThreshold") Double utilizationThreshold);
}