package com.k8s.monitor.repository;

import com.k8s.monitor.entity.ResourceMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Kubernetes 리소스 메트릭 Repository
 * Pod, Node, Container 등의 리소스 메트릭 데이터 접근
 */
@Repository
public interface MetricsRepository extends JpaRepository<ResourceMetrics, Long> {
    
    /**
     * 리소스 타입별 메트릭 조회
     */
    List<ResourceMetrics> findByResourceType(String resourceType);
    
    /**
     * 네임스페이스별 메트릭 조회
     */
    List<ResourceMetrics> findByNamespace(String namespace);
    
    /**
     * 노드별 메트릭 조회
     */
    List<ResourceMetrics> findByNodeName(String nodeName);
    
    /**
     * 특정 시간 이후의 메트릭 조회
     */
    @Query("SELECT m FROM ResourceMetrics m WHERE m.timestamp >= :since ORDER BY m.timestamp DESC")
    List<ResourceMetrics> findMetricsSince(@Param("since") LocalDateTime since);
    
    /**
     * 특정 시간 범위의 메트릭 조회
     */
    @Query("SELECT m FROM ResourceMetrics m WHERE m.timestamp BETWEEN :startTime AND :endTime ORDER BY m.timestamp DESC")
    List<ResourceMetrics> findMetricsBetween(@Param("startTime") LocalDateTime startTime, 
                                           @Param("endTime") LocalDateTime endTime);
    
    /**
     * 리소스 타입과 시간 범위로 메트릭 조회
     */
    @Query("SELECT m FROM ResourceMetrics m WHERE m.resourceType = :resourceType " +
           "AND m.timestamp BETWEEN :startTime AND :endTime ORDER BY m.timestamp DESC")
    List<ResourceMetrics> findMetricsByTypeAndTimeRange(@Param("resourceType") String resourceType,
                                                       @Param("startTime") LocalDateTime startTime,
                                                       @Param("endTime") LocalDateTime endTime);
    
    /**
     * 특정 리소스의 최신 메트릭 조회
     */
    @Query("SELECT m FROM ResourceMetrics m WHERE m.resourceType = :resourceType " +
           "AND m.resourceName = :resourceName " +
           "AND m.timestamp = (SELECT MAX(m2.timestamp) FROM ResourceMetrics m2 " +
           "WHERE m2.resourceType = :resourceType AND m2.resourceName = :resourceName)")
    List<ResourceMetrics> findLatestMetricsByResource(@Param("resourceType") String resourceType,
                                                     @Param("resourceName") String resourceName);
    
    /**
     * 네임스페이스와 시간 범위로 메트릭 조회
     */
    @Query("SELECT m FROM ResourceMetrics m WHERE m.namespace = :namespace " +
           "AND m.timestamp BETWEEN :startTime AND :endTime ORDER BY m.timestamp DESC")
    List<ResourceMetrics> findMetricsByNamespaceAndTimeRange(@Param("namespace") String namespace,
                                                            @Param("startTime") LocalDateTime startTime,
                                                            @Param("endTime") LocalDateTime endTime);
    
    /**
     * 높은 리소스 사용률 메트릭 조회
     */
    @Query("SELECT m FROM ResourceMetrics m WHERE " +
           "(m.cpuUsageCores / NULLIF(m.cpuLimitCores, 0) * 100 > :cpuThreshold " +
           "OR m.memoryUsageBytes / NULLIF(m.memoryLimitBytes, 0) * 100 > :memoryThreshold) " +
           "AND m.timestamp >= :since ORDER BY m.timestamp DESC")
    List<ResourceMetrics> findHighUsageMetrics(@Param("cpuThreshold") Double cpuThreshold,
                                              @Param("memoryThreshold") Double memoryThreshold,
                                              @Param("since") LocalDateTime since);
    
    /**
     * GPU 사용률이 높은 메트릭 조회 (GPU 관련 추가)
     */
    @Query("SELECT m FROM ResourceMetrics m WHERE " +
           "m.resourceType = 'GPU' AND " +
           "((m.cpuUsageCores / NULLIF(m.cpuLimitCores, 0) * 100 > :cpuThreshold) " +
           "OR (m.memoryUsageBytes / NULLIF(m.memoryLimitBytes, 0) * 100 > :memoryThreshold) " +
           "OR (m.gpuUsagePercent > :gpuThreshold)) " +
           "AND m.timestamp >= :since ORDER BY m.timestamp DESC")
    List<ResourceMetrics> findHighUsageMetrics(@Param("cpuThreshold") Double cpuThreshold,
                                              @Param("memoryThreshold") Double memoryThreshold,
                                              @Param("gpuThreshold") Double gpuThreshold,
                                              @Param("since") LocalDateTime since);
    
    /**
     * Pod별 평균 리소스 사용률 통계
     */
    @Query("SELECT m.resourceName, AVG(m.cpuUsageCores), AVG(m.memoryUsageBytes), COUNT(m) " +
           "FROM ResourceMetrics m WHERE m.resourceType = 'POD' " +
           "AND m.timestamp >= :since GROUP BY m.resourceName")
    List<Object[]> findPodUsageStatistics(@Param("since") LocalDateTime since);
    
    /**
     * 노드별 평균 리소스 사용률 통계
     */
    @Query("SELECT m.nodeName, AVG(m.cpuUsageCores), AVG(m.memoryUsageBytes), COUNT(m) " +
           "FROM ResourceMetrics m WHERE m.resourceType = 'NODE' " +
           "AND m.timestamp >= :since GROUP BY m.nodeName")
    List<Object[]> findNodeUsageStatistics(@Param("since") LocalDateTime since);
    
    /**
     * 네임스페이스별 리소스 사용률 통계
     */
    @Query("SELECT m.namespace, SUM(m.cpuUsageCores), SUM(m.memoryUsageBytes), COUNT(DISTINCT m.resourceName) " +
           "FROM ResourceMetrics m WHERE m.resourceType = 'POD' " +
           "AND m.namespace IS NOT NULL AND m.timestamp >= :since GROUP BY m.namespace")
    List<Object[]> findNamespaceUsageStatistics(@Param("since") LocalDateTime since);
    
    /**
     * 시간별 리소스 사용률 트렌드
     */
    @Query("SELECT EXTRACT(HOUR FROM m.timestamp), AVG(m.cpuUsageCores), AVG(m.memoryUsageBytes) " +
           "FROM ResourceMetrics m WHERE m.timestamp >= :since " +
           "GROUP BY EXTRACT(HOUR FROM m.timestamp) ORDER BY EXTRACT(HOUR FROM m.timestamp)")
    List<Object[]> findHourlyUsageTrend(@Param("since") LocalDateTime since);
    
    /**
     * 특정 기간 내 메트릭 개수 조회
     */
    @Query("SELECT COUNT(m) FROM ResourceMetrics m WHERE m.timestamp BETWEEN :start AND :end")
    long countMetricsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    
    /**
     * 리소스별 최신 메트릭 조회 (성능 최적화된 버전)
     */
    @Query(value = "SELECT * FROM resource_metrics m1 WHERE m1.timestamp = " +
                   "(SELECT MAX(m2.timestamp) FROM resource_metrics m2 " +
                   "WHERE m2.resource_type = m1.resource_type AND m2.resource_name = m1.resource_name) " +
                   "AND m1.resource_type = :resourceType", nativeQuery = true)
    List<ResourceMetrics> findLatestMetricsByType(@Param("resourceType") String resourceType);
    
    /**
     * 메트릭 데이터 정리 - 특정 날짜 이전 데이터 삭제
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM ResourceMetrics m WHERE m.timestamp < :cutoffDate")
    int deleteMetricsOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * 중복 메트릭 체크
     */
    boolean existsByResourceTypeAndResourceNameAndTimestamp(String resourceType, 
                                                           String resourceName, 
                                                           LocalDateTime timestamp);
    
    /**
     * 특정 리소스의 메트릭 개수 조회
     */
    @Query("SELECT COUNT(m) FROM ResourceMetrics m WHERE m.resourceType = :resourceType " +
           "AND m.resourceName = :resourceName")
    long countMetricsByResource(@Param("resourceType") String resourceType,
                               @Param("resourceName") String resourceName);
    
    /**
     * 리소스 이름으로 검색
     */
    List<ResourceMetrics> findByResourceNameContainingIgnoreCase(String resourceName);
    
    /**
     * 컬렉션 소스별 메트릭 조회
     */
    List<ResourceMetrics> findByCollectionSource(String collectionSource);
    
    /**
     * 최근 메트릭 데이터 존재 여부 확인
     */
    @Query("SELECT COUNT(m) > 0 FROM ResourceMetrics m WHERE m.timestamp >= :since")
    boolean hasRecentMetrics(@Param("since") LocalDateTime since);
    
    /**
     * 상위 CPU 사용률 리소스 조회
     */
    @Query("SELECT m FROM ResourceMetrics m WHERE m.cpuUsageCores IS NOT NULL " +
           "AND m.timestamp >= :since ORDER BY m.cpuUsageCores DESC")
    List<ResourceMetrics> findTopCpuUsageMetrics(@Param("since") LocalDateTime since);
    
    /**
     * 상위 메모리 사용률 리소스 조회
     */
    @Query("SELECT m FROM ResourceMetrics m WHERE m.memoryUsageBytes IS NOT NULL " +
           "AND m.timestamp >= :since ORDER BY m.memoryUsageBytes DESC")
    List<ResourceMetrics> findTopMemoryUsageMetrics(@Param("since") LocalDateTime since);
    
    /**
     * 특정 기간의 평균 사용률 계산
     */
    @Query("SELECT AVG(m.cpuUsageCores), AVG(m.memoryUsageBytes), COUNT(m) " +
           "FROM ResourceMetrics m WHERE m.resourceType = :resourceType " +
           "AND m.timestamp BETWEEN :startTime AND :endTime")
    Object[] findAverageUsageByTypeAndTimeRange(@Param("resourceType") String resourceType,
                                               @Param("startTime") LocalDateTime startTime,
                                               @Param("endTime") LocalDateTime endTime);
    
    /**
     * 모델 타입별 메트릭 조회 (GPU 관련)
     */
    @Query("SELECT m FROM ResourceMetrics m WHERE m.modelType = :modelType " +
           "AND m.timestamp >= :since ORDER BY m.timestamp DESC")
    List<ResourceMetrics> findMetricsByModelType(@Param("modelType") String modelType,
                                                @Param("since") LocalDateTime since);
    
    /**
     * GPU 사용률 백분율 조회 추가 (ResourceMetrics 엔티티에 필드가 있다면)
     */
    @Query("SELECT m FROM ResourceMetrics m WHERE m.gpuUsagePercent > :threshold " +
           "AND m.timestamp >= :since ORDER BY m.gpuUsagePercent DESC")
    List<ResourceMetrics> findHighGpuUsageMetrics(@Param("threshold") Double threshold,
                                                 @Param("since") LocalDateTime since);
    
    /**
     * 배치 삽입을 위한 메트릭 저장 (성능 최적화)
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO resource_metrics (resource_type, resource_name, namespace, node_name, " +
                   "timestamp, cpu_usage_cores, memory_usage_bytes, network_rx_bytes, network_tx_bytes, " +
                   "collection_source, created_at) VALUES " +
                   "(:resourceType, :resourceName, :namespace, :nodeName, :timestamp, " +
                   ":cpuUsage, :memoryUsage, :networkRx, :networkTx, :source, :createdAt)", 
           nativeQuery = true)
    void insertMetricsBatch(@Param("resourceType") String resourceType,
                           @Param("resourceName") String resourceName,
                           @Param("namespace") String namespace,
                           @Param("nodeName") String nodeName,
                           @Param("timestamp") LocalDateTime timestamp,
                           @Param("cpuUsage") Double cpuUsage,
                           @Param("memoryUsage") Long memoryUsage,
                           @Param("networkRx") Long networkRx,
                           @Param("networkTx") Long networkTx,
                           @Param("source") String source,
                           @Param("createdAt") LocalDateTime createdAt);
}