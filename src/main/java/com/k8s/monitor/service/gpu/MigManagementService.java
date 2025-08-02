package com.k8s.monitor.service.gpu;

import com.k8s.monitor.dto.gpu.*;
import com.k8s.monitor.entity.gpu.*;
import com.k8s.monitor.repository.gpu.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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

        // 기존 활성 할당이 있는지 확인
        if (hasActiveAllocations(deviceId)) {
            throw new RuntimeException("Cannot create MIG instances while device has active allocations");
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

            // 프로필별 최대 인스턴스 수만큼 생성
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
        
        // GPU를 일반 모드로 복원
        device.setDeviceStatus("ACTIVE");
        gpuDeviceRepository.save(device);

        log.info("Deleted {} MIG instances for device: {}", deletedCount, deviceId);
    }

    /**
     * 사용 가능한 MIG 인스턴스 조회
     */
    public List<MigInstanceInfo> getAvailableMigInstances() {
        return migInstanceRepository.findAvailableInstances().stream()
            .map(this::convertToMigDto)
            .collect(Collectors.toList());
    }

    /**
     * 특정 장비의 MIG 인스턴스 조회
     */
    public List<MigInstanceInfo> getMigInstancesByDevice(String deviceId) {
        return migInstanceRepository.findByDeviceDeviceId(deviceId).stream()
            .map(this::convertToMigDto)
            .collect(Collectors.toList());
    }

    /**
     * MIG 사용률 통계 조회
     */
    public Map<String, Object> getMigUsageStatistics() {
        List<Object[]> instanceCountByDevice = migInstanceRepository.findInstanceCountByDevice();
        List<Object[]> usageByProfile = migInstanceRepository.findUsageByProfile();
        
        Map<String, Object> deviceStats = instanceCountByDevice.stream()
            .collect(Collectors.toMap(
                row -> (String) row[0],
                row -> Map.of(
                    "total", ((Number) row[1]).intValue(),
                    "allocated", ((Number) row[2]).intValue()
                )
            ));
        
        Map<String, Object> profileStats = usageByProfile.stream()
            .collect(Collectors.toMap(
                row -> (String) row[0],
                row -> Map.of(
                    "total", ((Number) row[1]).intValue(),
                    "allocated", ((Number) row[2]).intValue()
                )
            ));
        
        long totalInstances = migInstanceRepository.count();
        long allocatedInstances = migInstanceRepository.findByAllocated("Y").size();
        
        return Map.of(
            "totalInstances", totalInstances,
            "allocatedInstances", allocatedInstances,
            "availableInstances", totalInstances - allocatedInstances,
            "utilizationPercent", totalInstances > 0 ? (double) allocatedInstances / totalInstances * 100 : 0,
            "deviceStatistics", deviceStats,
            "profileStatistics", profileStats,
            "lastUpdated", LocalDateTime.now()
        );
    }

    /**
     * 미사용 MIG 인스턴스 정리
     */
    @Transactional
    public int cleanupUnusedMigInstances(int unusedDays) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(unusedDays);
        List<MigInstance> unusedInstances = migInstanceRepository.findUnusedInstances(threshold);
        
        int cleanedCount = 0;
        for (MigInstance instance : unusedInstances) {
            instance.setAllocated("N");
            instance.setInstanceStatus("INACTIVE");
            migInstanceRepository.save(instance);
            cleanedCount++;
        }
        
        log.info("Cleaned up {} unused MIG instances", cleanedCount);
        return cleanedCount;
    }

    /**
     * MIG 프로필 정보 조회
     */
    public List<MigProfileInfo> getMigProfilesByModel(String modelId) {
        return migProfileRepository.findByModelModelId(modelId).stream()
            .map(this::convertToProfileDto)
            .collect(Collectors.toList());
    }

    // Private helper methods
    
    private boolean hasActiveAllocations(String deviceId) {
        // GPU 장비에 대한 직접 할당 확인
        List<GpuAllocation> deviceAllocations = allocationRepository.findAllocationHistory(deviceId);
        boolean hasDeviceAllocations = deviceAllocations.stream()
            .anyMatch(allocation -> "ALLOCATED".equals(allocation.getStatus()));
        
        // MIG 인스턴스에 대한 할당 확인
        List<MigInstance> migInstances = migInstanceRepository.findByDeviceDeviceId(deviceId);
        boolean hasMigAllocations = migInstances.stream()
            .anyMatch(instance -> "Y".equals(instance.getAllocated()));
        
        return hasDeviceAllocations || hasMigAllocations;
    }

    private MigInstanceInfo convertToMigDto(MigInstance instance) {
        return MigInstanceInfo.builder()
            .migId(instance.getMigId())
            .deviceId(instance.getDevice().getDeviceId())
            .profileId(instance.getProfile().getProfileId())
            .profileName(instance.getProfile().getProfileName())
            .instanceId(instance.getInstanceId())
            .migUuid(instance.getMigUuid())
            .allocated(Boolean.valueOf("Y".equals(instance.getAllocated())))
            .instanceStatus(instance.getInstanceStatus())
            .createdDate(instance.getCreatedDate())
            .allocatedDate(instance.getAllocatedDate())
            .lastUsedDate(instance.getLastUsedDate())
            .memoryGb(instance.getProfile().getMemoryGb())
            .computeSlices(instance.getProfile().getComputeSlices())
            .memorySlices(instance.getProfile().getMemorySlices())
            .performanceRatio(instance.getProfile().getPerformanceRatio())
            .build();
    }

    private MigProfileInfo convertToProfileDto(MigProfile profile) {
        return MigProfileInfo.builder()
            .profileId(profile.getProfileId())
            .modelId(profile.getModel().getModelId())
            .profileName(profile.getProfileName())
            .computeSlices(profile.getComputeSlices())
            .memorySlices(profile.getMemorySlices())
            .memoryGb(profile.getMemoryGb())
            .maxInstancesPerGpu(profile.getMaxInstancesPerGpu())
            .performanceRatio(profile.getPerformanceRatio())
            .useCase(profile.getUseCase())
            .description(profile.getDescription())
            .build();
    }

    private String generateMigInstanceId(String deviceId, int instanceId) {
        return String.format("%s-MIG-%02d", deviceId, instanceId);
    }
}