package com.k8s.monitor.service.gpu;

import com.k8s.monitor.entity.gpu.*;
import com.k8s.monitor.repository.gpu.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * GPU 최적화 서비스
 * GPU 리소스 최적화, 정리 작업 등의 기능 제공
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GpuOptimizationService {
    
    private final GpuDeviceRepository gpuDeviceRepository;
    private final MigInstanceRepository migInstanceRepository;
    private final GpuAllocationRepository allocationRepository;
    private final GpuUsageMetricsRepository metricsRepository;

    /**
     * 사용되지 않는 MIG 인스턴스 정리 (스케줄러)
     */
    @Scheduled(cron = "0 0 3 * * *") // 매일 새벽 3시 실행
    @Transactional
    public void cleanupUnusedMigInstances() {
        log.info("Starting unused MIG instances cleanup");
        
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);
        List<MigInstance> unusedInstances = migInstanceRepository.findUnusedInstances(threshold);
        
        int cleanedCount = 0;
        for (MigInstance instance : unusedInstances) {
            if ("Y".equals(instance.getAllocated()) && 
                (instance.getLastUsedDate() == null || instance.getLastUsedDate().isBefore(threshold))) {
                
                instance.setAllocated("N");
                instance.setInstanceStatus("INACTIVE");
                migInstanceRepository.save(instance);
                cleanedCount++;
            }
        }
        
        if (cleanedCount > 0) {
            log.info("Cleaned up {} unused MIG instances", cleanedCount);
        }
    }

    /**
     * 과도하게 할당된 리소스 감지
     */
    @Scheduled(fixedRate = 3600000) // 1시간마다 실행
    public void detectOverprovisionedResources() {
        log.debug("Detecting overprovisioned resources");
        
        List<GpuAllocation> activeAllocations = allocationRepository.findActiveAllocations();
        
        for (GpuAllocation allocation : activeAllocations) {
            if ("FULL_GPU".equals(allocation.getResourceType())) {
                checkGpuOverprovisioning(allocation);
            } else if ("MIG_INSTANCE".equals(allocation.getResourceType())) {
                checkMigOverprovisioning(allocation);
            }
        }
    }

    /**
     * 워크로드 밸런싱 최적화
     */
    @Scheduled(cron = "0 30 * * * *") // 매 시간 30분에 실행
    public void optimizeWorkloadBalancing() {
        log.debug("Optimizing workload balancing");
        
        // 노드별 GPU 사용률 확인
        List<Object[]> usageStatsByNode = metricsRepository.findUsageStatsByNode(LocalDateTime.now().minusHours(1));
        
        Map<String, Double> nodeUtilization = new HashMap<>();
        for (Object[] stat : usageStatsByNode) {
            String nodeName = (String) stat[0];
            Double avgUtilization = ((Number) stat[2]).doubleValue();
            nodeUtilization.put(nodeName, avgUtilization);
        }
        
        // 불균형 감지 및 권장사항 로깅
        detectAndLogImbalances(nodeUtilization);
    }

    /**
     * 비용 최적화 분석
     */
    public Map<String, Object> analyzeCostOptimization() {
        Map<String, Object> analysis = new HashMap<>();
        
        // 1. MIG 활용도 분석
        long totalGpus = gpuDeviceRepository.count();
        long migEnabledGpus = gpuDeviceRepository.findByDeviceStatus("MIG_ENABLED").size();
        double migAdoptionRate = totalGpus > 0 ? (double) migEnabledGpus / totalGpus * 100 : 0;
        
        analysis.put("migAdoptionRate", migAdoptionRate);
        analysis.put("migOptimizationPotential", migAdoptionRate < 30 ? "HIGH" : "MEDIUM");
        
        // 2. 유휴 리소스 분석
        List<GpuDevice> availableDevices = gpuDeviceRepository.findAvailableDevices();
        List<MigInstance> availableInstances = migInstanceRepository.findAvailableInstances();
        
        analysis.put("idleGpuDevices", availableDevices.size());
        analysis.put("idleMigInstances", availableInstances.size());
        
        // 3. 장기 실행 할당 분석
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        List<GpuAllocation> longRunningAllocations = allocationRepository.findLongRunningAllocations(weekAgo);
        
        analysis.put("longRunningAllocations", longRunningAllocations.size());
        analysis.put("optimizationRecommendation", generateOptimizationRecommendation(analysis));
        
        return analysis;
    }

    /**
     * 자동 최적화 실행
     */
    @Transactional
    public Map<String, Object> executeAutoOptimization() {
        Map<String, Object> results = new HashMap<>();
        
        try {
            // 1. 만료된 할당 정리
            int expiredAllocations = allocationRepository.expireOldAllocations();
            results.put("expiredAllocations", expiredAllocations);
            
            // 2. 미사용 MIG 인스턴스 정리
            cleanupUnusedMigInstances();
            results.put("cleanedMigInstances", "completed");
            
            // 3. 메트릭 데이터 정리
            LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
            int deletedMetrics = metricsRepository.deleteOldMetrics(cutoff);
            results.put("deletedOldMetrics", deletedMetrics);
            
            results.put("optimizationStatus", "SUCCESS");
            results.put("executedAt", LocalDateTime.now());
            
        } catch (Exception e) {
            log.error("Error during auto optimization: {}", e.getMessage(), e);
            results.put("optimizationStatus", "FAILED");
            results.put("error", e.getMessage());
        }
        
        return results;
    }

    // Private helper methods
    
    private void checkGpuOverprovisioning(GpuAllocation allocation) {
        // GPU 사용률이 낮은 할당 검사
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        Object[] avgUsage = metricsRepository.findAverageUsageByDevice(allocation.getAllocatedResource(), since);
        
        if (avgUsage != null && avgUsage[0] != null) {
            Double avgGpuUtilization = ((Number) avgUsage[0]).doubleValue();
            
            if (avgGpuUtilization < 30.0) { // 30% 미만 사용률
                log.warn("Low GPU utilization detected: {} ({}% avg utilization)", 
                        allocation.getAllocationId(), avgGpuUtilization);
            }
        }
    }

    private void checkMigOverprovisioning(GpuAllocation allocation) {
        // MIG 인스턴스 사용률 검사
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        List<GpuUsageMetrics> metrics = metricsRepository.findByMigInstanceAndTimeRange(
            allocation.getAllocatedResource(), since, LocalDateTime.now());
        
        if (!metrics.isEmpty()) {
            double avgUtilization = metrics.stream()
                .filter(m -> m.getGpuUtilizationPct() != null)
                .mapToDouble(GpuUsageMetrics::getGpuUtilizationPct)
                .average()
                .orElse(0.0);
            
            if (avgUtilization < 20.0) { // 20% 미만 사용률
                log.warn("Low MIG utilization detected: {} ({}% avg utilization)", 
                        allocation.getAllocationId(), avgUtilization);
            }
        }
    }

    private void detectAndLogImbalances(Map<String, Double> nodeUtilization) {
        if (nodeUtilization.size() < 2) return;
        
        double maxUtilization = nodeUtilization.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double minUtilization = nodeUtilization.values().stream().mapToDouble(Double::doubleValue).min().orElse(0);
        
        if (maxUtilization - minUtilization > 40.0) { // 40% 이상 차이
            log.warn("GPU workload imbalance detected: max={}%, min={}%", maxUtilization, minUtilization);
            
            // 가장 사용률이 낮은 노드와 높은 노드 찾기
            String lowUtilNode = nodeUtilization.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("unknown");
            
            String highUtilNode = nodeUtilization.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("unknown");
            
            log.info("Consider moving workloads from {} to {}", highUtilNode, lowUtilNode);
        }
    }

    private String generateOptimizationRecommendation(Map<String, Object> analysis) {
        List<String> recommendations = new ArrayList<>();
        
        Double migAdoptionRate = (Double) analysis.get("migAdoptionRate");
        if (migAdoptionRate < 30) {
            recommendations.add("MIG 사용률을 높여 리소스 효율성 개선");
        }
        
        Integer idleGpus = (Integer) analysis.get("idleGpuDevices");
        if (idleGpus > 2) {
            recommendations.add("유휴 GPU 장비 절전 모드 활용");
        }
        
        Integer longRunning = (Integer) analysis.get("longRunningAllocations");
        if (longRunning > 5) {
            recommendations.add("장기 실행 할당에 대한 리뷰 필요");
        }
        
        return recommendations.isEmpty() ? "현재 최적화 상태 양호" : String.join(", ", recommendations);
    }
}