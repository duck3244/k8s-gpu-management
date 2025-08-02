package com.k8s.monitor.repository.gpu;

import com.k8s.monitor.entity.gpu.GpuModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

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
    List<GpuModel> findByMemoryRange(@Param("minMemory") Integer minMemory, 
                                     @Param("maxMemory") Integer maxMemory);
    
    // 현재 활성 모델 조회 (EOL 날짜가 지나지 않은 모델)
    @Query("SELECT m FROM GpuModel m WHERE m.endOfLifeDate IS NULL OR m.endOfLifeDate > CURRENT_TIMESTAMP")
    List<GpuModel> findActiveModels();
    
    // 모델명으로 검색
    List<GpuModel> findByModelNameContainingIgnoreCase(String modelName);
    
    // 출시년도별 조회
    List<GpuModel> findByReleaseYear(Integer releaseYear);
    
    // 전력 소모량 범위별 조회
    @Query("SELECT m FROM GpuModel m WHERE m.powerConsumptionW BETWEEN :minPower AND :maxPower")
    List<GpuModel> findByPowerRange(@Param("minPower") Integer minPower, 
                                    @Param("maxPower") Integer maxPower);
    
    // 컴퓨트 능력별 조회
    List<GpuModel> findByComputeCapability(String computeCapability);
    
    // 제조사별 조회
    List<GpuModel> findByManufacturer(String manufacturer);
}