package com.k8s.monitor.service;

import com.k8s.monitor.dto.NodeResourceInfo;
import com.k8s.monitor.dto.PodResourceInfo;
import com.k8s.monitor.model.ResourceMetrics;
import com.k8s.monitor.repository.MetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 주기적인 메트릭 수집 및 저장을 담당하는 서비스
 * 스케줄링을 통해 자동으로 리소스 사용량을 수집하고 데이터베이스에 저장
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsCollectionService {
    
    private final KubernetesService kubernetesService;
    private final MetricsRepository metricsRepository;
    private final ResourceMetricsService resourceMetricsService;

    /**
     * 메인 메트릭 수집 스케줄러 (30초마다 실행)
     */
    @Scheduled(fixedRate = 30000) // 30초
    public void collectMetrics() {
        log.debug("Starting scheduled metrics collection...");
        
        try {
            collectPodMetrics();
            collectNodeMetrics();
            
            // 캐시 정리
            resourceMetricsService.clearExpiredCache();
            
            log.debug("Metrics collection completed successfully");
        } catch (Exception e) {
            log.error("Error during scheduled metrics collection: {}", e.getMessage(), e);
        }
    }

    /**
     * Pod 메트릭 수집 및 저장
     */
    @Transactional
    public void collectPodMetrics() {
        try {
            List<PodResourceInfo> pods = kubernetesService.getModelServingPods(null);
            log.debug("Collecting metrics for {} pods", pods.size());
            
            LocalDateTime timestamp = LocalDateTime.now();
            
            for (PodResourceInfo pod : pods) {
                try {
                    // 중복 체크
                    if (metricsRepository.existsByResourceTypeAndResourceNameAndTimestamp(
                        "POD", pod.getName(), timestamp)) {
                        continue;
                    }
                    
                    ResourceMetrics metrics = ResourceMetrics.builder()
                        .resourceType("POD")
                        .resourceName(pod.getName())
                        .namespace(pod.getNamespace())
                        .nodeName(pod.getNodeName())
                        .timestamp(timestamp)
                        .cpuUsage(pod.getCpuUsage())
                        .memoryUsage(pod.getMemoryUsage())
                        .gpuUsage(pod.getGpuUsage())
                        .cpuRequest(pod.getCpuRequest())
                        .memoryRequest(pod.getMemoryRequest())
                        .gpuRequest(pod.getGpuRequest())
                        .cpuLimit(pod.getCpuLimit())
                        .memoryLimit(pod.getMemoryLimit())
                        .gpuLimit(pod.getGpuLimit())
                        .cpuUsagePercent(pod.getCpuUsagePercent())
                        .memoryUsagePercent(pod.getMemoryUsagePercent())
                        .gpuUsagePercent(pod.getGpuUsagePercent())
                        .status(pod.getReadyStatus())
                        .phase(pod.getPhase())
                        .modelType(pod.getModelType())
                        .modelName(pod.getModelName())
                        .modelVersion(pod.getModelVersion())
                        .labels(convertMapToJson(pod.getLabels()))
                        .annotations(convertMapToJson(pod.getAnnotations()))
                        .build();
                        
                    metricsRepository.save(metrics);
                    
                } catch (Exception e) {
                    log.error("Error saving pod metrics for {}/{}: {}", 
                        pod.getNamespace(), pod.getName(), e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("Error collecting pod metrics: {}", e.getMessage(), e);
        }
    }

    /**
     * Node 메트릭 수집 및 저장
     */
    @Transactional
    public void collectNodeMetrics() {
        try {
            List<NodeResourceInfo> nodes = kubernetesService.getNodeResourceInfo();
            log.debug("Collecting metrics for {} nodes", nodes.size());
            
            LocalDateTime timestamp = LocalDateTime.now();
            
            for (NodeResourceInfo node : nodes) {
                try {
                    // 중복 체크
                    if (metricsRepository.existsByResourceTypeAndResourceNameAndTimestamp(
                        "NODE", node.getName(), timestamp)) {
                        continue;
                    }
                    
                    ResourceMetrics metrics = ResourceMetrics.builder()
                        .resourceType("NODE")
                        .resourceName(node.getName())
                        .timestamp(timestamp)
                        .cpuUsage(node.getCpuUsage())
                        .memoryUsage(node.getMemoryUsage())
                        .gpuUsage(node.getGpuUsage())
                        .storageUsage(node.getStorageUsage())
                        .cpuUsagePercent(node.getCpuUsagePercent())
                        .memoryUsagePercent(node.getMemoryUsagePercent())
                        .gpuUsagePercent(node.getGpuUsagePercent())
                        .storageUsagePercent(node.getStorageUsagePercent())
                        .status(node.getStatus())
                        .labels(convertMapToJson(node.getLabels()))
                        .annotations(convertMapToJson(node.getAnnotations()))
                        .build();
                        
                    metricsRepository.save(metrics);
                    
                } catch (Exception e) {
                    log.error("Error saving node metrics for {}: {}", node.getName(), e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("Error collecting node metrics: {}", e.getMessage(), e);
        }
    }

    /**
     * 오래된 메트릭 데이터 정리 (매일 자정 실행)
     */
    @Scheduled(cron = "0 0 0 * * *") // 매일 자정
    @Transactional
    public void cleanupOldMetrics() {
        try {
            // 7일 이전 데이터 삭제
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(7);
            int deletedCount = metricsRepository.deleteOldMetrics(cutoffTime);
            
            log.info("Cleaned up {} old metric records before {}", deletedCount, cutoffTime);
            
        } catch (Exception e) {
            log.error("Error during metrics cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * 메트릭 수집 상태 확인
     */
    public boolean isCollectionHealthy() {
        try {
            // 최근 5분 내에 수집된 메트릭이 있는지 확인
            LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
            long recentMetricsCount = metricsRepository.countMetricsBetween(fiveMinutesAgo, LocalDateTime.now());
            
            // 메트릭 서버 연결 상태도 확인
            boolean metricsServerAvailable = resourceMetricsService.isMetricsServerAvailable();
            
            // 최근 5분 내에 메트릭이 수집되었고, 메트릭 서버가 사용 가능하면 건강한 상태
            return recentMetricsCount > 0 && metricsServerAvailable;
            
        } catch (Exception e) {
            log.error("Error checking collection health: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 메트릭 수집 통계 조회
     */
    public Map<String, Object> getCollectionStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
            LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
            
            // 24시간 통계
            long dayMetricsCount = metricsRepository.countMetricsBetween(oneDayAgo, LocalDateTime.now());
            stats.put("metricsLast24Hours", dayMetricsCount);
            
            // 1시간 통계
            long hourMetricsCount = metricsRepository.countMetricsBetween(oneHourAgo, LocalDateTime.now());
            stats.put("metricsLastHour", hourMetricsCount);
            
            // 타입별 통계
            List<ResourceMetrics> recentMetrics = metricsRepository.findMetricsSince(oneHourAgo);
            long podMetricsCount = recentMetrics.stream()
                .filter(m -> "POD".equals(m.getResourceType()))
                .count();
            long nodeMetricsCount = recentMetrics.stream()
                .filter(m -> "NODE".equals(m.getResourceType()))
                .count();
            
            stats.put("podMetricsLastHour", podMetricsCount);
            stats.put("nodeMetricsLastHour", nodeMetricsCount);
            
            // 마지막 수집 시간
            Optional<ResourceMetrics> lastMetric = metricsRepository.findAll().stream()
                .max(Comparator.comparing(ResourceMetrics::getTimestamp));
            
            if (lastMetric.isPresent()) {
                stats.put("lastCollectionTime", lastMetric.get().getTimestamp());
            }
            
            stats.put("collectionHealthy", isCollectionHealthy());
            stats.put("timestamp", LocalDateTime.now());
            
        } catch (Exception e) {
            log.error("Error calculating collection statistics: {}", e.getMessage());
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }

    /**
     * 수동 메트릭 수집 트리거
     */
    public void triggerManualCollection() {
        log.info("Manual metrics collection triggered");
        try {
            collectMetrics();
            log.info("Manual metrics collection completed successfully");
        } catch (Exception e) {
            log.error("Error during manual metrics collection: {}", e.getMessage(), e);
            throw new RuntimeException("Manual metrics collection failed", e);
        }
    }

    /**
     * 특정 Pod의 메트릭 수집
     */
    @Transactional
    public void collectPodMetrics(String namespace, String podName) {
        try {
            PodResourceInfo pod = kubernetesService.getPodDetails(namespace, podName);
            if (pod != null) {
                log.info("Collecting metrics for specific pod: {}/{}", namespace, podName);
                
                ResourceMetrics metrics = createPodMetrics(pod, LocalDateTime.now());
                metricsRepository.save(metrics);
                
                log.debug("Successfully saved metrics for pod: {}/{}", namespace, podName);
            } else {
                log.warn("Pod not found: {}/{}", namespace, podName);
            }
        } catch (Exception e) {
            log.error("Error collecting metrics for pod {}/{}: {}", namespace, podName, e.getMessage());
        }
    }

    /**
     * 특정 Node의 메트릭 수집
     */
    @Transactional
    public void collectNodeMetrics(String nodeName) {
        try {
            NodeResourceInfo node = kubernetesService.getNodeDetails(nodeName);
            if (node != null) {
                log.info("Collecting metrics for specific node: {}", nodeName);
                
                ResourceMetrics metrics = createNodeMetrics(node, LocalDateTime.now());
                metricsRepository.save(metrics);
                
                log.debug("Successfully saved metrics for node: {}", nodeName);
            } else {
                log.warn("Node not found: {}", nodeName);
            }
        } catch (Exception e) {
            log.error("Error collecting metrics for node {}: {}", nodeName, e.getMessage());
        }
    }

    /**
     * 배치 메트릭 수집 (대용량 클러스터용)
     */
    @Transactional
    public void collectMetricsBatch() {
        log.info("Starting batch metrics collection");
        
        try {
            LocalDateTime timestamp = LocalDateTime.now();
            
            // Pod 메트릭 배치 수집
            List<PodResourceInfo> allPods = kubernetesService.getAllPods(null);
            List<ResourceMetrics> podMetricsList = allPods.stream()
                .map(pod -> createPodMetrics(pod, timestamp))
                .collect(Collectors.toList());
            
            if (!podMetricsList.isEmpty()) {
                metricsRepository.saveAll(podMetricsList);
                log.info("Saved {} pod metrics in batch", podMetricsList.size());
            }
            
            // Node 메트릭 배치 수집
            List<NodeResourceInfo> allNodes = kubernetesService.getNodeResourceInfo();
            List<ResourceMetrics> nodeMetricsList = allNodes.stream()
                .map(node -> createNodeMetrics(node, timestamp))
                .collect(Collectors.toList());
            
            if (!nodeMetricsList.isEmpty()) {
                metricsRepository.saveAll(nodeMetricsList);
                log.info("Saved {} node metrics in batch", nodeMetricsList.size());
            }
            
            log.info("Batch metrics collection completed successfully");
            
        } catch (Exception e) {
            log.error("Error during batch metrics collection: {}", e.getMessage(), e);
        }
    }

    // Private helper methods

    /**
     * Pod 메트릭 생성 헬퍼 메서드
     */
    private ResourceMetrics createPodMetrics(PodResourceInfo pod, LocalDateTime timestamp) {
        return ResourceMetrics.builder()
            .resourceType("POD")
            .resourceName(pod.getName())
            .namespace(pod.getNamespace())
            .nodeName(pod.getNodeName())
            .timestamp(timestamp)
            .cpuUsage(pod.getCpuUsage())
            .memoryUsage(pod.getMemoryUsage())
            .gpuUsage(pod.getGpuUsage())
            .cpuRequest(pod.getCpuRequest())
            .memoryRequest(pod.getMemoryRequest())
            .gpuRequest(pod.getGpuRequest())
            .cpuLimit(pod.getCpuLimit())
            .memoryLimit(pod.getMemoryLimit())
            .gpuLimit(pod.getGpuLimit())
            .cpuUsagePercent(pod.getCpuUsagePercent())
            .memoryUsagePercent(pod.getMemoryUsagePercent())
            .gpuUsagePercent(pod.getGpuUsagePercent())
            .status(pod.getReadyStatus())
            .phase(pod.getPhase())
            .modelType(pod.getModelType())
            .modelName(pod.getModelName())
            .modelVersion(pod.getModelVersion())
            .labels(convertMapToJson(pod.getLabels()))
            .annotations(convertMapToJson(pod.getAnnotations()))
            .build();
    }

    /**
     * Node 메트릭 생성 헬퍼 메서드
     */
    private ResourceMetrics createNodeMetrics(NodeResourceInfo node, LocalDateTime timestamp) {
        return ResourceMetrics.builder()
            .resourceType("NODE")
            .resourceName(node.getName())
            .timestamp(timestamp)
            .cpuUsage(node.getCpuUsage())
            .memoryUsage(node.getMemoryUsage())
            .gpuUsage(node.getGpuUsage())
            .storageUsage(node.getStorageUsage())
            .cpuUsagePercent(node.getCpuUsagePercent())
            .memoryUsagePercent(node.getMemoryUsagePercent())
            .gpuUsagePercent(node.getGpuUsagePercent())
            .storageUsagePercent(node.getStorageUsagePercent())
            .status(node.getStatus())
            .labels(convertMapToJson(node.getLabels()))
            .annotations(convertMapToJson(node.getAnnotations()))
            .build();
    }

    /**
     * Map을 JSON 문자열로 변환
     */
    private String convertMapToJson(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        
        try {
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            
            for (Map.Entry<String, String> entry : map.entrySet()) {
                if (!first) {
                    json.append(",");
                }
                json.append("\"").append(escapeJsonString(entry.getKey())).append("\":\"")
                    .append(escapeJsonString(entry.getValue())).append("\"");
                first = false;
            }
            
            json.append("}");
            return json.toString();
            
        } catch (Exception e) {
            log.error("Error converting map to JSON: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * JSON 문자열 이스케이프 처리
     */
    private String escapeJsonString(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}