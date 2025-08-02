// ============================================================================
// GPU Management Repositories - Data Access Layer
// ============================================================================

package com.k8s.monitor.repository.gpu;

import com.k8s.monitor.entity.gpu.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * GPU 모델 정보 Repository
 */
@Repository
public interface GpuModelRepository extends JpaRepository<GpuModel, String> {
    
    // 아키텍처별 조회
    List<GpuModel> findByArchitecture(String architecture);
    
    // MIG 지원 모델 조회
    List<GpuModel> findByMigSupport(String migSupport);
    
    // 시장 세그먼트별 조회
    List<GpuModel> findByMarketSegment(String marketSegment);
    
    // 메모리 용량 범위 조회
    @Query("SELECT m FROM GpuModel m WHERE m.memoryGb BETWEEN :minMemory AND :maxMemory")
    List<GpuModel> findByMemoryRange(@Param("minMemory") Integer minMemory, @Param("maxMemory") Integer maxMemory);
    
    // 현재 활성 모델 조회 (EOL 날짜가 지나지 않은 모델)
    @Query("SELECT m FROM GpuModel m WHERE m.endOfLifeDate IS NULL OR m.endOfLifeDate > CURRENT_TIMESTAMP")
    List<GpuModel> findActiveModels();
    
    // 모델명으로 검색
    List<GpuModel> findByModelNameContainingIgnoreCase(String modelName);
}

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
}

/**
 * GPU 장비 정보 Repository
 */
@Repository
public interface GpuDeviceRepository extends JpaRepository<GpuDevice, String> {
    
    // 노드별 GPU 장비 조회
    List<GpuDevice> findByNodeNodeName(String nodeName);
    
    // 모델별 GPU 장비 조회
    List<GpuDevice> findByModelModelId(String modelId);
    
    // GPU UUID로 조회
    Optional<GpuDevice> findByGpuUuid(String gpuUuid);
    
    // 상태별 GPU 장비 조회
    List<GpuDevice> findByDeviceStatus(String deviceStatus);
    
    // 사용 가능한 GPU 장비 조회
    @Query("SELECT d FROM GpuDevice d WHERE d.deviceStatus = 'ACTIVE' AND d.deviceId NOT IN " +
           "(SELECT a.allocatedResource FROM GpuAllocation a WHERE a.status = 'ALLOCATED' AND a.resourceType = 'FULL_GPU')")
    List<GpuDevice> findAvailableDevices();
    
    // MIG 지원 GPU 장비 조회
    @Query("SELECT d FROM GpuDevice d WHERE d.model.migSupport = 'Y'")
    List<GpuDevice> findMigCapableDevices();
    
    // MIG 활성화된 GPU 장비 조회
    List<GpuDevice> findByDeviceStatus(String deviceStatus);
    
    // 온도 임계값 초과 장비 조회
    @Query("SELECT d FROM GpuDevice d WHERE d.currentTempC > :threshold")
    List<GpuDevice> findOverheatingDevices(@Param("threshold") Double threshold);
    
    // 전력 소모량 상위 장비 조회
    @Query("SELECT d FROM GpuDevice d WHERE d.currentPowerW IS NOT NULL ORDER BY d.currentPowerW DESC")
    List<GpuDevice> findHighPowerConsumptionDevices();
    
    // 노드별 GPU 개수 통계
    @Query("SELECT d.node.nodeName, COUNT(d), " +
           "SUM(CASE WHEN d.deviceStatus = 'ACTIVE' THEN 1 ELSE 0 END) " +
           "FROM GpuDevice d GROUP BY d.node.nodeName")
    List<Object[]> findDeviceCountByNode();
    
    // 모델별 GPU 개수 통계
    @Query("SELECT d.model.modelName, COUNT(d), " +
           "SUM(CASE WHEN d.deviceStatus = 'ACTIVE' THEN 1 ELSE 0 END) " +
           "FROM GpuDevice d GROUP BY d.model.modelName")
    List<Object[]> findDeviceCountByModel();
    
    // 보증 만료 예정 장비 조회
    @Query("SELECT d FROM GpuDevice d WHERE d.warrantyExpiryDate BETWEEN CURRENT_TIMESTAMP AND :expiryDate")
    List<GpuDevice> findDevicesWithExpiringWarranty(@Param("expiryDate") LocalDateTime expiryDate);
}

/**
 * MIG 프로필 Repository
 */
@Repository
public interface MigProfileRepository extends JpaRepository<MigProfile, String> {
    
    // 모델별 MIG 프로필 조회
    List<MigProfile> findByModelModelId(String modelId);
    
    // 프로필명으로 조회
    Optional<MigProfile> findByModelModelIdAndProfileName(String modelId, String profileName);
    
    // 메모리 크기별 조회
    List<MigProfile> findByMemoryGb(Integer memoryGb);
    
    // 사용 사례별 조회
    List<MigProfile> findByUseCase(String useCase);
    
    // 메모리 범위별 조회
    @Query("SELECT p FROM MigProfile p WHERE p.memoryGb BETWEEN :minMemory AND :maxMemory")
    List<MigProfile> findByMemoryRange(@Param("minMemory") Integer minMemory, @Param("maxMemory") Integer maxMemory);
    
    // 최대 인스턴스 수별 조회
    @Query("SELECT p FROM MigProfile p WHERE p.maxInstancesPerGpu >= :minInstances")
    List<MigProfile> findByMinInstanceCount(@Param("minInstances") Integer minInstances);
}

/**
 * MIG 인스턴스 Repository
 */
@Repository
public interface MigInstanceRepository extends JpaRepository<MigInstance, String> {
    
    // 장비별 MIG 인스턴스 조회
    List<MigInstance> findByDeviceDeviceId(String deviceId);
    
    // 프로필별 MIG 인스턴스 조회
    List<MigInstance> findByProfileProfileId(String profileId);
    
    // 할당 상태별 조회
    List<MigInstance> findByAllocated(String allocated);
    
    // 사용 가능한 MIG 인스턴스 조회
    @Query("SELECT m FROM MigInstance m WHERE m.allocated = 'N' AND m.instanceStatus = 'ACTIVE'")
    List<MigInstance> findAvailableInstances();
    
    // 특정 장비의 사용 가능한 MIG 인스턴스 조회
    @Query("SELECT m FROM MigInstance m WHERE m.device.deviceId = :deviceId " +
           "AND m.allocated = 'N' AND m.instanceStatus = 'ACTIVE'")
    List<MigInstance> findAvailableInstancesByDevice(@Param("deviceId") String deviceId);
    
    // MIG UUID로 조회
    Optional<MigInstance> findByMigUuid(String migUuid);
    
    // 장비별 MIG 인스턴스 개수 통계
    @Query("SELECT m.device.deviceId, COUNT(m), " +
           "SUM(CASE WHEN m.allocated = 'Y' THEN 1 ELSE 0 END) " +
           "FROM MigInstance m GROUP BY m.device.deviceId")
    List<Object[]> findInstanceCountByDevice();
    
    // 프로필별 사용률 통계
    @Query("SELECT m.profile.profileName, COUNT(m), " +
           "SUM(CASE WHEN m.allocated = 'Y' THEN 1 ELSE 0 END) " +
           "FROM MigInstance m GROUP BY m.profile.profileName")
    List<Object[]> findUsageByProfile();
    
    // 오래 사용되지 않은 MIG 인스턴스 조회
    @Query("SELECT m FROM MigInstance m WHERE m.allocated = 'Y' " +
           "AND (m.lastUsedDate IS NULL OR m.lastUsedDate < :threshold)")
    List<MigInstance> findUnusedInstances(@Param("threshold") LocalDateTime threshold);
    
    // 특정 장비의 모든 MIG 인스턴스 삭제
    @Modifying
    @Transactional
    @Query("DELETE FROM MigInstance m WHERE m.device.deviceId = :deviceId")
    int deleteByDeviceId(@Param("deviceId") String deviceId);
}

/**
 * GPU 할당 정보 Repository
 */
@Repository
public interface GpuAllocationRepository extends JpaRepository<GpuAllocation, String> {
    
    // 네임스페이스별 할당 조회
    List<GpuAllocation> findByNamespace(String namespace);
    
    // Pod별 할당 조회
    List<GpuAllocation> findByNamespaceAndPodName(String namespace, String podName);
    
    // 상태별 할당 조회
    List<GpuAllocation> findByStatus(String status);
    
    // 활성 할당 조회
    @Query("SELECT a FROM GpuAllocation a WHERE a.status = 'ALLOCATED'")
    List<GpuAllocation> findActiveAllocations();
    
    // 만료된 할당 조회
    @Query("SELECT a FROM GpuAllocation a WHERE a.status = 'ALLOCATED' " +
           "AND a.plannedReleaseTime < CURRENT_TIMESTAMP")
    List<GpuAllocation> findExpiredAllocations();
    
    // 사용자별 할당 조회
    List<GpuAllocation> findByUserId(String userId);
    
    // 팀별 할당 조회
    List<GpuAllocation> findByTeamId(String teamId);
    
    // 프로젝트별 할당 조회
    List<GpuAllocation> findByProjectId(String projectId);
    
    // 워크로드 타입별 할당 조회
    List<GpuAllocation> findByWorkloadType(String workloadType);
    
    // 리소스 타입별 할당 조회
    List<GpuAllocation> findByResourceType(String resourceType);
    
    // 특정 기간 내 할당 조회
    @Query("SELECT a FROM GpuAllocation a WHERE a.allocationTime BETWEEN :startTime AND :endTime")
    List<GpuAllocation> findByAllocationTimeBetween(@Param("startTime") LocalDateTime startTime, 
                                                    @Param("endTime") LocalDateTime endTime);
    
    // 비용 범위별 할당 조회
    @Query("SELECT a FROM GpuAllocation a WHERE a.totalCost BETWEEN :minCost AND :maxCost")
    List<GpuAllocation> findByCostRange(@Param("minCost") Double minCost, @Param("maxCost") Double maxCost);
    
    // 네임스페이스별 비용 통계
    @Query("SELECT a.namespace, COUNT(a), SUM(a.totalCost), AVG(a.totalCost) " +
           "FROM GpuAllocation a WHERE a.totalCost IS NOT NULL GROUP BY a.namespace")
    List<Object[]> findCostStatsByNamespace();
    
    // 팀별 비용 통계
    @Query("SELECT a.teamId, COUNT(a), SUM(a.totalCost), AVG(a.totalCost) " +
           "FROM GpuAllocation a WHERE a.teamId IS NOT NULL AND a.totalCost IS NOT NULL GROUP BY a.teamId")
    List<Object[]> findCostStatsByTeam();
    
    // 워크로드별 사용 통계
    @Query("SELECT a.workloadType, COUNT(a), " +
           "AVG(EXTRACT(EPOCH FROM (COALESCE(a.releaseTime, CURRENT_TIMESTAMP) - a.allocationTime)) / 3600) " +
           "FROM GpuAllocation a GROUP BY a.workloadType")
    List<Object[]> findUsageStatsByWorkload();
    
    // 특정 리소스의 할당 히스토리
    @Query("SELECT a FROM GpuAllocation a WHERE a.allocatedResource = :resourceId ORDER BY a.allocationTime DESC")
    List<GpuAllocation> findAllocationHistory(@Param("resourceId") String resourceId);
    
    // 만료 예정 할당 조회
    @Query("SELECT a FROM GpuAllocation a WHERE a.status = 'ALLOCATED' " +
           "AND a.plannedReleaseTime BETWEEN CURRENT_TIMESTAMP AND :expiryTime")
    List<GpuAllocation> findAllocationsExpiringBefore(@Param("expiryTime") LocalDateTime expiryTime);
    
    // 장기간 사용 중인 할당 조회
    @Query("SELECT a FROM GpuAllocation a WHERE a.status = 'ALLOCATED' " +
           "AND a.allocationTime < :threshold")
    List<GpuAllocation> findLongRunningAllocations(@Param("threshold") LocalDateTime threshold);
    
    // 만료된 할당 자동 해제
    @Modifying
    @Transactional
    @Query("UPDATE GpuAllocation a SET a.status = 'EXPIRED', a.releaseTime = CURRENT_TIMESTAMP, " +
           "a.updatedDate = CURRENT_TIMESTAMP WHERE a.status = 'ALLOCATED' " +
           "AND a.plannedReleaseTime < CURRENT_TIMESTAMP")
    int expireOldAllocations();
}

/**
 * GPU 사용량 메트릭 Repository
 */
@Repository
public interface GpuUsageMetricsRepository extends JpaRepository<GpuUsageMetrics, Long> {
    
    // 장비별 최신 메트릭 조회
    @Query("SELECT m FROM GpuUsageMetrics m WHERE m.device.deviceId = :deviceId " +
           "AND m.timestamp = (SELECT MAX(m2.timestamp) FROM GpuUsageMetrics m2 WHERE m2.device.deviceId = :deviceId)")
    Optional<GpuUsageMetrics> findLatestByDevice(@Param("deviceId") String deviceId);
    
    // MIG 인스턴스별 최신 메트릭 조회
    @Query("SELECT m FROM GpuUsageMetrics m WHERE m.migInstance.migId = :migId " +
           "AND m.timestamp = (SELECT MAX(m2.timestamp) FROM GpuUsageMetrics m2 WHERE m2.migInstance.migId = :migId)")
    Optional<GpuUsageMetrics> findLatestByMigInstance(@Param("migId") String migId);
    
    // 장비별 시계열 메트릭 조회
    @Query("SELECT m FROM GpuUsageMetrics m WHERE m.device.deviceId = :deviceId " +
           "AND m.timestamp BETWEEN :startTime AND :endTime ORDER BY m.timestamp DESC")
    List<GpuUsageMetrics> findByDeviceAndTimeRange(@Param("deviceId") String deviceId,
                                                   @Param("startTime") LocalDateTime startTime,
                                                   @Param("endTime") LocalDateTime endTime);
    
    // MIG 인스턴스별 시계열 메트릭 조회
    @Query("SELECT m FROM GpuUsageMetrics m WHERE m.migInstance.migId = :migId " +
           "AND m.timestamp BETWEEN :startTime AND :endTime ORDER BY m.timestamp DESC")
    List<GpuUsageMetrics> findByMigInstanceAndTimeRange(@Param("migId") String migId,
                                                        @Param("startTime") LocalDateTime startTime,
                                                        @Param("endTime") LocalDateTime endTime);
    
    // 특정 시간 이후의 모든 메트릭 조회
    @Query("SELECT m FROM GpuUsageMetrics m WHERE m.timestamp >= :since ORDER BY m.timestamp DESC")
    List<GpuUsageMetrics> findMetricsSince(@Param("since") LocalDateTime since);
    
    // 높은 사용률 메트릭 조회
    @Query("SELECT m FROM GpuUsageMetrics m WHERE " +
           "(m.gpuUtilizationPct > :gpuThreshold OR m.memoryUtilizationPct > :memoryThreshold) " +
           "AND m.timestamp >= :since ORDER BY m.timestamp DESC")
    List<GpuUsageMetrics> findHighUsageMetrics(@Param("gpuThreshold") Double gpuThreshold,
                                              @Param("memoryThreshold") Double memoryThreshold,
                                              @Param("since") LocalDateTime since);
    
    // 과열 상태 메트릭 조회
    @Query("SELECT m FROM GpuUsageMetrics m WHERE m.temperatureC > :tempThreshold " +
           "AND m.timestamp >= :since ORDER BY m.timestamp DESC")
    List<GpuUsageMetrics> findOverheatingMetrics(@Param("tempThreshold") Double tempThreshold,
                                                 @Param("since") LocalDateTime since);
    
    // 평균 사용률 계산
    @Query("SELECT AVG(m.gpuUtilizationPct), AVG(m.memoryUtilizationPct), AVG(m.temperatureC), AVG(m.powerDrawW) " +
           "FROM GpuUsageMetrics m WHERE m.device.deviceId = :deviceId AND m.timestamp >= :since")
    Object[] findAverageUsageByDevice(@Param("deviceId") String deviceId, @Param("since") LocalDateTime since);
    
    // 장비별 사용률 통계
    @Query("SELECT m.device.deviceId, m.device.model.modelName, " +
           "AVG(m.gpuUtilizationPct), AVG(m.memoryUtilizationPct), AVG(m.temperatureC) " +
           "FROM GpuUsageMetrics m WHERE m.timestamp >= :since " +
           "GROUP BY m.device.deviceId, m.device.model.modelName")
    List<Object[]> findUsageStatsByDevice(@Param("since") LocalDateTime since);
    
    // 모델별 사용률 통계
    @Query("SELECT m.device.model.modelName, COUNT(DISTINCT m.device.deviceId), " +
           "AVG(m.gpuUtilizationPct), AVG(m.memoryUtilizationPct), AVG(m.temperatureC) " +
           "FROM GpuUsageMetrics m WHERE m.timestamp >= :since " +
           "GROUP BY m.device.model.modelName")
    List<Object[]> findUsageStatsByModel(@Param("since") LocalDateTime since);
    
    // 시간별 사용률 트렌드
    @Query("SELECT EXTRACT(HOUR FROM m.timestamp), " +
           "AVG(m.gpuUtilizationPct), AVG(m.memoryUtilizationPct), AVG(m.temperatureC) " +
           "FROM GpuUsageMetrics m WHERE m.timestamp >= :since " +
           "GROUP BY EXTRACT(HOUR FROM m.timestamp) ORDER BY EXTRACT(HOUR FROM m.timestamp)")
    List<Object[]> findHourlyUsageTrend(@Param("since") LocalDateTime since);
    
    // 상위 사용률 장비 조회
    @Query("SELECT m.device.deviceId, m.device.model.modelName, AVG(m.gpuUtilizationPct) " +
           "FROM GpuUsageMetrics m WHERE m.timestamp >= :since " +
           "GROUP BY m.device.deviceId, m.device.model.modelName " +
           "ORDER BY AVG(m.gpuUtilizationPct) DESC")
    List<Object[]> findTopUtilizationDevices(@Param("since") LocalDateTime since);
    
    // 오래된 메트릭 데이터 삭제
    @Modifying
    @Transactional
    @Query("DELETE FROM GpuUsageMetrics m WHERE m.timestamp < :cutoff")
    int deleteOldMetrics(@Param("cutoff") LocalDateTime cutoff);
    
    // 중복 메트릭 체크
    boolean existsByDeviceDeviceIdAndTimestamp(String deviceId, LocalDateTime timestamp);
    boolean existsByMigInstanceMigIdAndTimestamp(String migId, LocalDateTime timestamp);
    
    // 특정 기간 내 메트릭 개수 조회
    @Query("SELECT COUNT(m) FROM GpuUsageMetrics m WHERE m.timestamp BETWEEN :start AND :end")
    long countMetricsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    
    // 노드별 GPU 메트릭 요약
    @Query("SELECT m.device.node.nodeName, COUNT(DISTINCT m.device.deviceId), " +
           "AVG(m.gpuUtilizationPct), AVG(m.memoryUtilizationPct), AVG(m.temperatureC) " +
           "FROM GpuUsageMetrics m WHERE m.timestamp >= :since " +
           "GROUP BY m.device.node.nodeName")
    List<Object[]> findUsageStatsByNode(@Param("since") LocalDateTime since);
}
    