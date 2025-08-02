// ============================================================================
// GPU Management Services - Business Logic Layer
// ============================================================================

package com.k8s.monitor.service.gpu;

import com.k8s.monitor.dto.gpu.*;
import com.k8s.monitor.entity.gpu.*;
import com.k8s.monitor.repository.gpu.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * GPU 장비 관리 서비스
 * GPU 장비 등록, 조회, 상태 관리 등의 기능 제공
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GpuDeviceService {
    
    private final GpuDeviceRepository gpuDeviceRepository;
    private final GpuModelRepository gpuModelRepository;
    private final GpuNodeRepository gpuNodeRepository;
    private final GpuUsageMetricsRepository metricsRepository;

    /**
     * 모든 GPU 장비 조회
     */
    public List<GpuDeviceInfo> getAllGpuDevices() {
        return gpuDeviceRepository.findAll().stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }

    /**
     * 노드별 GPU 장비 조회
     */
    public List<GpuDeviceInfo> getGpuDevicesByNode(String nodeName) {
        return gpuDeviceRepository.findByNodeNodeName(nodeName).stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }

    /**
     * 모델별 GPU 장비 조회
     */
    public List<GpuDeviceInfo> getGpuDevicesByModel(String modelId) {
        return gpuDeviceRepository.findByModelModelId(modelId).stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }

    /**
     * 사용 가능한 GPU 장비 조회
     */
    public List<GpuDeviceInfo> getAvailableGpuDevices() {
        return gpuDeviceRepository.findAvailableDevices().stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }

    /**
     * GPU 장비 상세 정보 조회
     */
    public GpuDeviceInfo getGpuDeviceDetails(String deviceId) {
        GpuDevice device = gpuDeviceRepository.findById(deviceId)
            .orElseThrow(() -> new RuntimeException("GPU device not found: " + deviceId));
        
        GpuDeviceInfo deviceInfo = convertToDto(device);
        
        // 최신 메트릭 정보 추가
        Optional<GpuUsageMetrics> latestMetrics = metricsRepository.findLatestByDevice(deviceId);
        if (latestMetrics.isPresent()) {
            GpuUsageMetrics metrics = latestMetrics.get();
            deviceInfo.setCurrentUtilization(metrics.getGpuUtilizationPct());
            deviceInfo.setCurrentTemperature(metrics.getTemperatureC());
            deviceInfo.setCurrentPowerDraw(metrics.getPowerDrawW());
            deviceInfo.setMemoryUtilization(metrics.getMemoryUtilizationPct());
        }
        
        return deviceInfo;
    }

    /**
     * GPU 장비 등록
     */
    @Transactional
    public GpuDeviceInfo registerGpuDevice(GpuDeviceRegistrationRequest request) {
        // 노드 존재 확인
        GpuNode node = gpuNodeRepository.findByNodeName(request.getNodeName())
            .orElseThrow(() -> new RuntimeException("Node not found: " + request.getNodeName()));

        // 모델 존재 확인
        GpuModel model = gpuModelRepository.findById(request.getModelId())
            .orElseThrow(() -> new RuntimeException("GPU model not found: " + request.getModelId()));

        // 중복 확인
        if (gpuDeviceRepository.findByGpuUuid(request.getGpuUuid()).isPresent()) {
            throw new RuntimeException("GPU device already exists: " + request.getGpuUuid());
        }

        GpuDevice device = GpuDevice.builder()
            .deviceId(generateDeviceId(node.getNodeName(), request.getDeviceIndex()))
            .node(node)
            .model(model)
            .deviceIndex(request.getDeviceIndex())
            .serialNumber(request.getSerialNumber())
            .pciAddress(request.getPciAddress())
            .gpuUuid(request.getGpuUuid())
            .deviceStatus("ACTIVE")
            .driverVersion(request.getDriverVersion())
            .firmwareVersion(request.getFirmwareVersion())
            .installationDate(LocalDateTime.now())
            .build();

        device = gpuDeviceRepository.save(device);
        
        // 노드의 GPU 개수 업데이트
        updateNodeGpuCount(node.getNodeId());
        
        log.info("GPU device registered: {}", device.getDeviceId());
        return convertToDto(device);
    }

    /**
     * GPU 장비 상태 업데이트
     */
    @Transactional
    public void updateGpuDeviceStatus(String deviceId, String status) {
        GpuDevice device = gpuDeviceRepository.findById(deviceId)
            .orElseThrow(() -> new RuntimeException("GPU device not found: " + deviceId));
        
        device.setDeviceStatus(status);
        gpuDeviceRepository.save(device);
        
        log.info("GPU device status updated: {} -> {}", deviceId, status);
    }

    /**
     * GPU 온도 및 전력 정보 업데이트
     */
    @Transactional
    public void updateGpuDeviceMetrics(String deviceId, Double temperature, Double powerDraw) {
        GpuDevice device = gpuDeviceRepository.findById(deviceId)
            .orElseThrow(() -> new RuntimeException("GPU device not found: " + deviceId));
        
        device.setCurrentTempC(temperature);
        device.setCurrentPowerW(powerDraw);
        gpuDeviceRepository.save(device);
    }

    /**
     * 과열 상태 GPU 장비 조회
     */
    public List<GpuDeviceInfo> getOverheatingDevices() {
        return gpuDeviceRepository.findOverheatingDevices(85.0).stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }

    /**
     * GPU 장비 통계 조회
     */
    public GpuDeviceStatistics getGpuDeviceStatistics() {
        List<Object[]> deviceCountByNode = gpuDeviceRepository.findDeviceCountByNode();
        List<Object[]> deviceCountByModel = gpuDeviceRepository.findDeviceCountByModel();
        
        Map<String, Integer> nodeStats = deviceCountByNode.stream()
            .collect(Collectors.toMap(
                row -> (String) row[0],
                row -> ((Number) row[1]).intValue()
            ));
        
        Map<String, Integer> modelStats = deviceCountByModel.stream()
            .collect(Collectors.toMap(
                row -> (String) row[0],
                row -> ((Number) row[1]).intValue()
            ));
        
        long totalDevices = gpuDeviceRepository.count();
        long activeDevices = gpuDeviceRepository.findByDeviceStatus("ACTIVE").size();
        long migEnabledDevices = gpuDeviceRepository.findByDeviceStatus("MIG_ENABLED").size();
        
        return GpuDeviceStatistics.builder()
            .totalDevices((int) totalDevices)
            .activeDevices((int) activeDevices)
            .migEnabledDevices((int) migEnabledDevices)
            .devicesByNode(nodeStats)
            .devicesByModel(modelStats)
            .build();
    }

    // Private helper methods
    
    private GpuDeviceInfo convertToDto(GpuDevice device) {
        return GpuDeviceInfo.builder()
            .deviceId(device.getDeviceId())
            .nodeName(device.getNode().getNodeName())
            .modelId(device.getModel().getModelId())
            .modelName(device.getModel().getModelName())
            .deviceIndex(device.getDeviceIndex())
            .serialNumber(device.getSerialNumber())
            .pciAddress(device.getPciAddress())
            .gpuUuid(device.getGpuUuid())
            .deviceStatus(device.getDeviceStatus())
            .currentTempC(device.getCurrentTempC())
            .currentPowerW(device.getCurrentPowerW())
            .driverVersion(device.getDriverVersion())
            .firmwareVersion(device.getFirmwareVersion())
            .migSupport(device.getModel().supportsMig())
            .memoryGb(device.getModel().getMemoryGb())
            .architecture(device.getModel().getArchitecture())
            .installationDate(device.getInstallationDate())
            .lastMaintenanceDate(device.getLastMaintenanceDate())
            .build();
    }

    private String generateDeviceId(String nodeName, Integer deviceIndex) {
        return String.format("%s-GPU-%02d", nodeName, deviceIndex);
    }

    private void updateNodeGpuCount(String nodeId) {
        GpuNode node = gpuNodeRepository.findById(nodeId)
            .orElseThrow(() -> new RuntimeException("Node not found: " + nodeId));
        
        List<GpuDevice> devices = gpuDeviceRepository.findByNodeNodeName(node.getNodeName());
        int totalGpus = devices.size();
        int activeGpus = (int) devices.stream()
            .filter(device -> "ACTIVE".equals(device.getDeviceStatus()))
            .count();
        
        node.setTotalGpus(totalGpus);
        node.setAvailableGpus(activeGpus);
        gpuNodeRepository.save(node);
    }
}

/**
 * MIG 관리 서비스
 * MIG 인스턴스 생성, 삭제, 할당 관리 등의 기능 제공
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MigManagementService {
    
    private final GpuDeviceRepository gpuDeviceRepository;
    private final MigProfileRepository migProfileRepository;
    private final MigInstanceRepository migInstanceRepository;
    private final GpuAllocationRepository allocationRepository;

    /**
     * MIG 인스턴스 생성
     */
    @Transactional
    public List<MigInstanceInfo> createMigInstances(String deviceId, List<String> profileIds) {
        GpuDevice device = gpuDeviceRepository.findById(deviceId)
            .orElseThrow(() -> new RuntimeException("GPU device not found: " + deviceId));

        if (!device.getModel().supportsMig()) {
            throw new RuntimeException("GPU model does not support MIG: " + device.getModel().getModelName());
        }

        // 기존 MIG 인스턴스 삭제
        migInstanceRepository.deleteByDeviceId(deviceId);

        // GPU를 MIG 모드로 변경
        device.setDeviceStatus("MIG_ENABLED");
        gpuDeviceRepository.save(device);

        List<MigInstanceInfo> createdInstances = new ArrayList<>();
        int instanceIdCounter = 0;

        for (String profileId : profileIds) {
            MigProfile profile = migProfileRepository.findById(profileId)
                .orElseThrow(() -> new RuntimeException("MIG profile not found: " + profileId));

            for (int i = 0; i < profile.getMaxInstancesPerGpu(); i++) {
                MigInstance instance = MigInstance.builder()
                    .migId(generateMigInstanceId(deviceId, instanceIdCounter))
                    .device(device)
                    .profile(profile)
                    .instanceId(instanceIdCounter)
                    .migUuid("MIG-" + UUID.randomUUID().toString())
                    .allocated("N")
                    .instanceStatus("ACTIVE")
                    .build();

                instance = migInstanceRepository.save(instance);
                createdInstances.add(convertToMigDto(instance));
                instanceIdCounter++;
            }
        }

        log.info("Created {} MIG instances for device: {}", createdInstances.size(), deviceId);
        return createdInstances;
    }

    /**
     * MIG 인스턴스 삭제
     */
    @Transactional
    public void deleteMigInstances(String deviceId) {
        GpuDevice device = gpuDeviceRepository.findById(deviceId)
            .orElseThrow(() -> new RuntimeException("GPU device not found: " + deviceId));

        // 활성 할당이 있는지 확인
        List<MigInstance> instances = migInstanceRepository.findByDeviceDeviceId(deviceId);
        boolean hasActiveAllocations = instances.stream()
            .anyMatch(instance -> "Y".equals(instance.getAllocated()));

        if (hasActiveAllocations) {
            throw new RuntimeException("Cannot delete MIG instances with active allocations");
        }

        int deletedCount = migInstanceRepository.deleteByDeviceId(deviceId);
        
        //