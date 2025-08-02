package com.k8s.monitor.repository.gpu;

import com.k8s.monitor.entity.gpu.MigProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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
    List<MigProfile> findByMemoryRange(@Param("minMemory") Integer minMemory, 
                                       @Param("maxMemory") Integer maxMemory);
    
    // 최대 인스턴스 수별 조회
    @Query("SELECT p FROM MigProfile p WHERE p.maxInstancesPerGpu >= :minInstances")
    List<MigProfile> findByMinInstanceCount(@Param("minInstances") Integer minInstances);
    
    // 컴퓨트 슬라이스별 조회
    List<MigProfile> findByComputeSlices(Integer computeSlices);
    
    // 메모리 슬라이스별 조회
    List<MigProfile> findByMemorySlices(Integer memorySlices);
    
    // 성능 비율 범위별 조회
    @Query("SELECT p FROM MigProfile p WHERE p.performanceRatio BETWEEN :minRatio AND :maxRatio")
    List<MigProfile> findByPerformanceRatioRange(@Param("minRatio") Double minRatio, 
                                                  @Param("maxRatio") Double maxRatio);
    
    // 프로필명으로 검색
    List<MigProfile> findByProfileNameContainingIgnoreCase(String profileName);
}