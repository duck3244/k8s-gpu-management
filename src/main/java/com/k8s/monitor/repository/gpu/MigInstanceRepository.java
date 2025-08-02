package com.k8s.monitor.repository.gpu;

import com.k8s.monitor.entity.gpu.MigInstance;
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
    
    // 인스턴스 상태별 조회
    List<MigInstance> findByInstanceStatus(String instanceStatus);
    
    // 특정 장비의 특정 인스턴스 ID 조회
    Optional<MigInstance> findByDeviceDeviceIdAndInstanceId(String deviceId, Integer instanceId);
    
    // 생성일 범위별 조회
    @Query("SELECT m FROM MigInstance m WHERE m.createdDate BETWEEN :startDate AND :endDate")
    List<MigInstance> findByCreatedDateRange(@Param("startDate") LocalDateTime startDate, 
                                            @Param("endDate") LocalDateTime endDate);
    
    // 활성 MIG 인스턴스 조회
    @Query("SELECT m FROM MigInstance m WHERE m.instanceStatus = 'ACTIVE'")
    List<MigInstance> findActiveInstances();
}