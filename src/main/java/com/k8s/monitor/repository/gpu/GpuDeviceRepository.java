package com.k8s.monitor.repository.gpu;

import com.k8s.monitor.entity.gpu.GpuDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
    @Query("SELECT d FROM GpuDevice d WHERE d.deviceStatus = 'MIG_ENABLED'")
    List<GpuDevice> findMigEnabledDevices();
    
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
    
    // 시리얼 번호로 조회
    Optional<GpuDevice> findBySerialNumber(String serialNumber);
    
    // PCI 주소로 조회
    Optional<GpuDevice> findByPciAddress(String pciAddress);
    
    // 드라이버 버전별 조회
    List<GpuDevice> findByDriverVersion(String driverVersion);
    
    // 설치일 범위별 조회
    @Query("SELECT d FROM GpuDevice d WHERE d.installationDate BETWEEN :startDate AND :endDate")
    List<GpuDevice> findByInstallationDateRange(@Param("startDate") LocalDateTime startDate, 
                                                @Param("endDate") LocalDateTime endDate);
}