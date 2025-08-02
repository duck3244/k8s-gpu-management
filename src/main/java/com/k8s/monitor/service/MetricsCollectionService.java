package com.k8s.monitor.service;

import com.k8s.monitor.entity.ResourceMetrics;
import com.k8s.monitor.repository.MetricsRepository;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.custom.Quantity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Kubernetes 리소스 메트릭 수집 서비스 (수정된 버전)
 * Pod, Node, Container 등의 리소스 메트릭을 주기적으로 수집
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsCollectionService {
    
    private final MetricsRepository metricsRepository;
    private final CoreV1Api coreV1Api;
    private final ResourceMetricsService resourceMetricsService;

    /**
     * 메트릭 수집 스케줄러 (5분마다 실행)
     */
    @Scheduled(fixedRate = 300000) // 5분마다 실행
    @Transactional
    public void collectMetrics() {
        try {
            log.debug("Starting metrics collection");
            
            LocalDateTime collectionTime = LocalDateTime.now();
            
            // Pod 메트릭 수집
            List<ResourceMetrics> podMetrics = collectPodMetrics(collectionTime);
            if (!podMetrics.isEmpty()) {
                saveMetricsSafely(podMetrics);
            }
            
            // Node 메트릭 수집
            List<ResourceMetrics> nodeMetrics = collectNodeMetrics(collectionTime);
            if (!nodeMetrics.isEmpty()) {
                saveMetricsSafely(nodeMetrics);
            }
            
            log.debug("Metrics collection completed. Collected {} pod metrics, {} node metrics", 
                     podMetrics.size(), nodeMetrics.size());
            
        } catch (Exception e) {
            log.error("Error during metrics collection: {}", e.getMessage(), e);
        }
    }

    /**
     * 수동 메트릭 수집 트리거
     */
    public void triggerMetricsCollection() {
        log.info("Manual metrics collection triggered");
        collectMetrics();
    }

    /**
     * 오래된 메트릭 데이터 정리 (매일 새벽 3시 실행)
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupOldMetrics() {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(30); // 30일 이전 데이터 삭제
            
            // 수정된 메서드명 사용
            int deletedCount = metricsRepository.deleteMetricsOlderThan(cutoffTime);
            
            if (deletedCount > 0) {
                log.info("Cleaned up {} old metrics records", deletedCount);
            }
            
        } catch (Exception e) {
            log.error("Error cleaning up old metrics: {}", e.getMessage(), e);
        }
    }

    /**
     * 메트릭 서버 상태 확인
     */
    public boolean isMetricsServerAvailable() {
        try {
            LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
            
            // 수정된 메서드명 사용
            return metricsRepository.hasRecentMetrics(oneHourAgo);
            
        } catch (Exception e) {
            log.warn("Error checking metrics server availability: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 최근 메트릭 통계 조회
     */
    public Map<String, Object> getRecentMetricsStatistics() {
        try {
            LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
            
            // 수정된 메서드명 사용
            List<ResourceMetrics> recentMetrics = metricsRepository.findMetricsSince(oneHourAgo);
            
            return calculateMetricsStatistics(recentMetrics);
            
        } catch (Exception e) {
            log.error("Error getting recent metrics statistics: {}", e.getMessage(), e);
            return Map.of("error", "Failed to retrieve statistics");
        }
    }

    // Private helper methods

    private List<ResourceMetrics> collectPodMetrics(LocalDateTime collectionTime) {
        List<ResourceMetrics> podMetricsList = new ArrayList<>();
        
        try {
            // Kubernetes API를 통한 Pod 메트릭 수집
            var podList = coreV1Api.listPodForAllNamespaces(
                null, null, null, null, null, null, null, null, null, null, null);
            
            for (var pod : podList.getItems()) {
                try {
                    ResourceMetrics metrics = createPodMetrics(pod, collectionTime);
                    if (metrics != null) {
                        podMetricsList.add(metrics);
                    }
                } catch (Exception e) {
                    log.warn("Error creating metrics for pod {}: {}", 
                            pod.getMetadata().getName(), e.getMessage());
                }
            }
            
        } catch (ApiException e) {
            log.error("Error collecting pod metrics: {}", e.getMessage());
        }
        
        return podMetricsList;
    }

    private List<ResourceMetrics> collectNodeMetrics(LocalDateTime collectionTime) {
        List<ResourceMetrics> nodeMetricsList = new ArrayList<>();
        
        try {
            // Kubernetes API를 통한 Node 메트릭 수집
            var nodeList = coreV1Api.listNode(
                null, null, null, null, null, null, null, null, null, null, null);
            
            for (var node : nodeList.getItems()) {
                try {
                    ResourceMetrics metrics = createNodeMetrics(node, collectionTime);
                    if (metrics != null) {
                        nodeMetricsList.add(metrics);
                    }
                } catch (Exception e) {
                    log.warn("Error creating metrics for node {}: {}", 
                            node.getMetadata().getName(), e.getMessage());
                }
            }
            
        } catch (ApiException e) {
            log.error("Error collecting node metrics: {}", e.getMessage());
        }
        
        return nodeMetricsList;
    }

    private ResourceMetrics createPodMetrics(io.kubernetes.client.openapi.models.V1Pod pod, 
                                           LocalDateTime collectionTime) {
        try {
            var metadata = pod.getMetadata();
            var spec = pod.getSpec();
            
            if (metadata == null || spec == null) {
                return null;
            }
            
            // 리소스 사용량 정보 수집 (실제로는 Metrics Server API 호출 필요)
            Map<String, String> podMetrics = resourceMetricsService.getPodMetrics(
                metadata.getNamespace(), metadata.getName());
            
            return ResourceMetrics.builder()
                .resourceType("POD")
                .resourceName(metadata.getName())
                .namespace(metadata.getNamespace())
                .nodeName(spec.getNodeName())
                .timestamp(collectionTime)
                .cpuUsageCores(parseResourceValue(podMetrics.get("cpu")))
                .memoryUsageBytes(parseMemoryValue(podMetrics.get("memory")))
                .networkRxBytes(parseNetworkValue(podMetrics.get("network_rx")))
                .networkTxBytes(parseNetworkValue(podMetrics.get("network_tx")))
                .collectionSource("kubernetes-api")
                .build();
                
        } catch (Exception e) {
            log.warn("Error creating pod metrics: {}", e.getMessage());
            return null;
        }
    }

    private ResourceMetrics createNodeMetrics(io.kubernetes.client.openapi.models.V1Node node, 
                                            LocalDateTime collectionTime) {
        try {
            var metadata = node.getMetadata();
            var status = node.getStatus();
            
            if (metadata == null || status == null) {
                return null;
            }
            
            // 노드 리소스 사용량 정보 수집
            Map<String, String> nodeMetrics = resourceMetricsService.getNodeMetrics(metadata.getName());
            
            return ResourceMetrics.builder()
                .resourceType("NODE")
                .resourceName(metadata.getName())
                .nodeName(metadata.getName())
                .timestamp(collectionTime)
                .cpuUsageCores(parseResourceValue(nodeMetrics.get("cpu")))
                .memoryUsageBytes(parseMemoryValue(nodeMetrics.get("memory")))
                .storageUsageBytes(parseStorageValue(nodeMetrics.get("storage")))
                .collectionSource("kubernetes-api")
                .build();
                
        } catch (Exception e) {
            log.warn("Error creating node metrics: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 메트릭 안전하게 저장 (중복 체크 포함)
     */
    private void saveMetricsSafely(List<ResourceMetrics> metricsList) {
        List<ResourceMetrics> metricsToSave = new ArrayList<>();
        
        for (ResourceMetrics metrics : metricsList) {
            try {
                // 중복 메트릭 체크
                if (!metricsRepository.existsByResourceTypeAndResourceNameAndTimestamp(
                        metrics.getResourceType(), 
                        metrics.getResourceName(), 
                        metrics.getTimestamp())) {
                    
                    metricsToSave.add(metrics);
                }
            } catch (Exception e) {
                log.warn("Error checking metric existence for {}: {}", 
                        metrics.getResourceName(), e.getMessage());
            }
        }
        
        // 배치로 저장
        if (!metricsToSave.isEmpty()) {
            try {
                metricsRepository.saveAll(metricsToSave);
                log.debug("Saved {} metrics", metricsToSave.size());
            } catch (Exception e) {
                log.error("Error saving metrics batch: {}", e.getMessage(), e);
                
                // 개별 저장으로 fallback
                saveMetricsIndividually(metricsToSave);
            }
        }
    }

    /**
     * 개별 메트릭 저장 (배치 저장 실패시 fallback)
     */
    private void saveMetricsIndividually(List<ResourceMetrics> metricsList) {
        int savedCount = 0;
        
        for (ResourceMetrics metrics : metricsList) {
            try {
                metricsRepository.save(metrics);
                savedCount++;
            } catch (Exception e) {
                log.warn("Error saving individual metric for {}: {}", 
                        metrics.getResourceName(), e.getMessage());
            }
        }
        
        log.debug("Individually saved {} out of {} metrics", savedCount, metricsList.size());
    }

    /**
     * 메트릭 통계 계산
     */
    private Map<String, Object> calculateMetricsStatistics(List<ResourceMetrics> recentMetrics) {
        try {
            long podMetricsCount = recentMetrics.stream()
                .filter(m -> "POD".equals(m.getResourceType()))
                .count();
            
            long nodeMetricsCount = recentMetrics.stream()
                .filter(m -> "NODE".equals(m.getResourceType()))
                .count();
            
            double avgCpuUsage = recentMetrics.stream()
                .filter(m -> m.getCpuUsageCores() != null)
                .mapToDouble(ResourceMetrics::getCpuUsageCores)
                .average()
                .orElse(0.0);
            
            long avgMemoryUsage = recentMetrics.stream()
                .filter(m -> m.getMemoryUsageBytes() != null)
                .mapToLong(ResourceMetrics::getMemoryUsageBytes)
                .sum() / Math.max(1, recentMetrics.size());
            
            return Map.of(
                "totalMetrics", recentMetrics.size(),
                "podMetrics", podMetricsCount,
                "nodeMetrics", nodeMetricsCount,
                "avgCpuUsage", avgCpuUsage,
                "avgMemoryUsage", avgMemoryUsage,
                "lastUpdated", LocalDateTime.now()
            );
            
        } catch (Exception e) {
            log.error("Error calculating metrics statistics: {}", e.getMessage());
            return Map.of(
                "error", "Failed to calculate statistics",
                "lastUpdated", LocalDateTime.now()
            );
        }
    }

    // 리소스 값 파싱 유틸리티 메서드들

    private Double parseResourceValue(String value) {
        if (value == null || value.isEmpty() || "0".equals(value)) {
            return 0.0;
        }
        
        try {
            // CPU 코어 값 파싱 (예: "500m" -> 0.5)
            if (value.endsWith("m")) {
                return Double.parseDouble(value.substring(0, value.length() - 1)) / 1000.0;
            }
            
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            log.warn("Error parsing resource value '{}': {}", value, e.getMessage());
            return 0.0;
        }
    }

    private Long parseMemoryValue(String value) {
        if (value == null || value.isEmpty() || "0".equals(value)) {
            return 0L;
        }
        
        try {
            // 메모리 값 파싱 (예: "1Gi" -> bytes)
            String numericPart = value.replaceAll("[^0-9.]", "");
            double numValue = Double.parseDouble(numericPart);
            
            if (value.contains("Ki")) {
                return (long) (numValue * 1024);
            } else if (value.contains("Mi")) {
                return (long) (numValue * 1024 * 1024);
            } else if (value.contains("Gi")) {
                return (long) (numValue * 1024 * 1024 * 1024);
            }
            
            return (long) numValue;
        } catch (NumberFormatException e) {
            log.warn("Error parsing memory value '{}': {}", value, e.getMessage());
            return 0L;
        }
    }

    private Long parseNetworkValue(String value) {
        if (value == null || value.isEmpty()) {
            return 0L;
        }
        
        try {
            return Long.parseLong(value.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            log.warn("Error parsing network value '{}': {}", value, e.getMessage());
            return 0L;
        }
    }

    private Long parseStorageValue(String value) {
        return parseMemoryValue(value); // 메모리와 동일한 파싱 로직 사용
    }

    /**
     * 메트릭 수집 상태 조회
     */
    public Map<String, Object> getCollectionStatus() {
        try {
            LocalDateTime lastHour = LocalDateTime.now().minusHours(1);
            long recentMetricsCount = metricsRepository.countMetricsBetween(lastHour, LocalDateTime.now());
            
            boolean isHealthy = recentMetricsCount > 0;
            
            return Map.of(
                "status", isHealthy ? "HEALTHY" : "UNHEALTHY",
                "recentMetricsCount", recentMetricsCount,
                "metricsServerAvailable", isMetricsServerAvailable(),
                "lastCheck", LocalDateTime.now()
            );
            
        } catch (Exception e) {
            log.error("Error getting collection status: {}", e.getMessage());
            return Map.of(
                "status", "ERROR",
                "error", e.getMessage(),
                "lastCheck", LocalDateTime.now()
            );
        }
    }

    /**
     * 특정 리소스의 메트릭 조회
     */
    public List<ResourceMetrics> getResourceMetrics(String resourceType, String resourceName, int hours) {
        try {
            LocalDateTime since = LocalDateTime.now().minusHours(hours);
            
            return metricsRepository.findLatestMetricsByResource(resourceType, resourceName);
            
        } catch (Exception e) {
            log.error("Error getting resource metrics for {}/{}: {}", resourceType, resourceName, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 메트릭 수집 강제 실행 (테스트용)
     */
    public Map<String, Object> forceCollectMetrics() {
        try {
            log.info("Force collecting metrics");
            
            LocalDateTime startTime = LocalDateTime.now();
            collectMetrics();
            LocalDateTime endTime = LocalDateTime.now();
            
            long duration = java.time.Duration.between(startTime, endTime).toMillis();
            
            return Map.of(
                "status", "SUCCESS",
                "duration", duration + "ms",
                "timestamp", endTime
            );
            
        } catch (Exception e) {
            log.error("Error in force collect metrics: {}", e.getMessage(), e);
            return Map.of(
                "status", "ERROR",
                "error", e.getMessage(),
                "timestamp", LocalDateTime.now()
            );
        }
    }
}