package com.k8s.monitor.repository.gpu;

import com.k8s.monitor.entity.gpu.GpuAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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
    List<GpuAllocation> findByCostRange(@Param("minCost") Double minCost, 
                                        @Param("maxCost") Double maxCost);
    
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
    
    // 우선순위별 할당 조회
    List<GpuAllocation> findByPriorityClass(String priorityClass);
    
    // 컨테이너별 할당 조회
    List<GpuAllocation> findByContainerName(String containerName);
    
    // 사용자의 활성 할당 조회
    @Query("SELECT a FROM GpuAllocation a WHERE a.userId = :userId AND a.status = 'ALLOCATED'")
    List<GpuAllocation> findActiveAllocationsByUser(@Param("userId") String userId);
}