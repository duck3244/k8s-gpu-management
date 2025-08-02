package com.k8s.monitor.repository.gpu;

import com.k8s.monitor.entity.gpu.GpuUsageMetrics;
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
    
    // 할당별 메트릭 조회
    @Query("SELECT m FROM GpuUsageMetrics m WHERE m.allocation.allocationId = :allocationId " +
           "AND m.timestamp BETWEEN :startTime AND :endTime ORDER BY m.timestamp DESC")
    List<GpuUsageMetrics> findByAllocationAndTimeRange(@Param("allocationId") String allocationId,
                                                       @Param("startTime") LocalDateTime startTime,
                                                       @Param("endTime") LocalDateTime endTime);
    
    // 컬렉션 소스별 메트릭 조회
    List<GpuUsageMetrics> findByCollectionSource(String collectionSource);
    
    // 특정 온도 범위의 메트릭 조회
    @Query("SELECT m FROM GpuUsageMetrics m WHERE m.temperatureC BETWEEN :minTemp AND :maxTemp " +
           "AND m.timestamp >= :since")
    List<GpuUsageMetrics> findByTemperatureRange(@Param("minTemp") Double minTemp,
                                                 @Param("maxTemp") Double maxTemp,
                                                 @Param("since") LocalDateTime since);
    
    // 전력 사용량이 높은 메트릭 조회
    @Query("SELECT m FROM GpuUsageMetrics m WHERE m.powerDrawW > :powerThreshold " +
           "AND m.timestamp >= :since ORDER BY m.powerDrawW DESC")
    List<GpuUsageMetrics> findHighPowerUsageMetrics(@Param("powerThreshold") Double powerThreshold,
                                                    @Param("since") LocalDateTime since);
    
    // 메모리 사용률이 높은 메트릭 조회  
    @Query("SELECT m FROM GpuUsageMetrics m WHERE m.memoryUtilizationPct > :memoryThreshold " +
           "AND m.timestamp >= :since ORDER BY m.memoryUtilizationPct DESC")
    List<GpuUsageMetrics> findHighMemoryUsageMetrics(@Param("memoryThreshold") Double memoryThreshold,
                                                     @Param("since") LocalDateTime since);
}