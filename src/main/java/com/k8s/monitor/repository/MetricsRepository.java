package com.k8s.monitor.repository;

import com.k8s.monitor.model.ResourceMetrics;
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
 * 리소스 메트릭 데이터 접근을 위한 Repository
 * 시계열 데이터 조회 및 분석을 위한 커스텀 쿼리 제공
 */
@Repository
public interface MetricsRepository extends JpaRepository<ResourceMetrics, Long> {
    
    // 특정 리소스의 최신 메트릭 조회
    Optional<ResourceMetrics> findFirstByResourceTypeAndResourceNameOrderByTimestampDesc(
        String resourceType, String resourceName);
    
    // 특정 리소스의 시계열 메트릭 조회
    List<ResourceMetrics> findByResourceTypeAndResourceNameOrderByTimestampDesc(
        String resourceType, String resourceName);
    
    // 리소스 타입별 최신 메트릭 조회
    @Query("SELECT m FROM ResourceMetrics m WHERE m.resourceType = :type AND m.timestamp = " +
           "(SELECT MAX(m2.timestamp) FROM ResourceMetrics m2 WHERE m2.resourceType = :type AND m2.resourceName = m.resourceName)")
    List<ResourceMetrics> findLatestMetricsByType(@Param("type") String resourceType);
    
    // 특정 시간 이후의 메트릭 조회
    @Query("SELECT m FROM ResourceMetrics m WHERE m.timestamp >= :since ORDER BY m.timestamp DESC")
    List<ResourceMetrics> findMetricsSince(@Param("since") LocalDateTime since);
    
    // 특정 리소스 타입과 시간 범위의 메트릭 조회
    @Query("SELECT m FROM ResourceMetrics m WHERE m.resourceType = :type AND m.timestamp BETWEEN :start AND :end ORDER BY m.timestamp DESC")
    List<ResourceMetrics> findMetricsByTypeAndTimeRange(
        @Param("type") String resourceType, 
        @Param("start") LocalDateTime start, 
        @Param("end") LocalDateTime end);
    
    // 네임스페이스별 Pod 메트릭 조회
    @Query("SELECT m FROM ResourceMetrics m WHERE m.resourceType = 'POD' AND m.namespace = :namespace AND m.timestamp >= :since ORDER BY m.timestamp DESC")
    List<ResourceMetrics> findPodMetricsByNamespaceSince(
        @Param("namespace") String namespace, 
        @Param("since") LocalDateTime since);
    
    // 모델 타입별 메트릭 조회
    @Query("SELECT m FROM ResourceMetrics m WHERE m.modelType = :modelType AND m.timestamp >= :since ORDER BY m.timestamp DESC")
    List<ResourceMetrics> findMetricsByModelTypeSince(
        @Param("modelType") String modelType, 
        @Param("since") LocalDateTime since);
    
    // 리소스 사용률이 임계값을 초과하는 메트릭 조회
    @Query("SELECT m FROM ResourceMetrics m WHERE " +
           "(m.cpuUsagePercent > :cpuThreshold OR m.memoryUsagePercent > :memoryThreshold OR m.gpuUsagePercent > :gpuThreshold) " +
           "AND m.timestamp >= :since ORDER BY m.timestamp DESC")
    List<ResourceMetrics> findHighUsageMetrics(
        @Param("cpuThreshold") Double cpuThreshold,
        @Param("memoryThreshold") Double memoryThreshold,
        @Param("gpuThreshold") Double gpuThreshold,
        @Param("since") LocalDateTime since);
    
    // 평균 리소스 사용률 계산
    @Query("SELECT AVG(m.cpuUsagePercent), AVG(m.memoryUsagePercent), AVG(m.gpuUsagePercent) " +
           "FROM ResourceMetrics m WHERE m.resourceType = :type AND m.timestamp >= :since")
    Object[] findAverageUsageByType(@Param("type") String resourceType, @Param("since") LocalDateTime since);
    
    // 모델별 리소스 사용량 집계
    @Query("SELECT m.modelType, COUNT(DISTINCT m.resourceName), " +
           "AVG(m.cpuUsagePercent), AVG(m.memoryUsagePercent), AVG(m.gpuUsagePercent) " +
           "FROM ResourceMetrics m WHERE m.resourceType = 'POD' AND m.modelType IS NOT NULL " +
           "AND m.timestamp >= :since GROUP BY m.modelType")
    List<Object[]> findModelResourceSummary(@Param("since") LocalDateTime since);
    
    // 노드별 Pod 개수 및 리소스 사용량
    @Query("SELECT m.nodeName, COUNT(DISTINCT m.resourceName), " +
           "AVG(m.cpuUsagePercent), AVG(m.memoryUsagePercent), AVG(m.gpuUsagePercent) " +
           "FROM ResourceMetrics m WHERE m.resourceType = 'POD' AND m.nodeName IS NOT NULL " +
           "AND m.timestamp >= :since GROUP BY m.nodeName")
    List<Object[]> findNodeResourceSummary(@Param("since") LocalDateTime since);
    
    // 상위 리소스 사용 Pod 조회
    @Query("SELECT m.resourceName, m.namespace, m.cpuUsagePercent " +
           "FROM ResourceMetrics m WHERE m.resourceType = 'POD' AND m.timestamp >= :since " +
           "ORDER BY m.cpuUsagePercent DESC")
    List<Object[]> findTopCpuUsagePods(@Param("since") LocalDateTime since);
    
    @Query("SELECT m.resourceName, m.namespace, m.memoryUsagePercent " +
           "FROM ResourceMetrics m WHERE m.resourceType = 'POD' AND m.timestamp >= :since " +
           "ORDER BY m.memoryUsagePercent DESC")
    List<Object[]> findTopMemoryUsagePods(@Param("since") LocalDateTime since);
    
    @Query("SELECT m.resourceName, m.namespace, m.gpuUsagePercent " +
           "FROM ResourceMetrics m WHERE m.resourceType = 'POD' AND m.timestamp >= :since " +
           "ORDER BY m.gpuUsagePercent DESC")
    List<Object[]> findTopGpuUsagePods(@Param("since") LocalDateTime since);
    
    // 시계열 데이터 정리 (오래된 데이터 삭제)
    @Modifying
    @Transactional
    @Query("DELETE FROM ResourceMetrics m WHERE m.timestamp < :cutoff")
    int deleteOldMetrics(@Param("cutoff") LocalDateTime cutoff);
    
    // 중복 메트릭 체크 (동일 시간, 동일 리소스)
    boolean existsByResourceTypeAndResourceNameAndTimestamp(
        String resourceType, String resourceName, LocalDateTime timestamp);
    
    // 특정 기간 내 메트릭 개수 조회
    @Query("SELECT COUNT(m) FROM ResourceMetrics m WHERE m.timestamp BETWEEN :start AND :end")
    long countMetricsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}