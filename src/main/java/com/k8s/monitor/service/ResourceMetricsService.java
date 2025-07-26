package com.k8s.monitor.service;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.custom.Quantity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Kubernetes Metrics Server에서 실시간 리소스 사용량을 수집하는 서비스
 * Pod과 Node의 CPU, 메모리, GPU 사용량을 실시간으로 조회
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceMetricsService {
    
    private final ApiClient apiClient;

    /**
     * Pod의 현재 메트릭 조회
     * @param namespace Pod 네임스페이스
     * @param podName Pod 이름
     * @return 리소스 사용량 맵 (cpu, memory, gpu)
     */
    public Map<String, String> getPodMetrics(String namespace, String podName) {
        Map<String, String> metrics = new HashMap<>();
        
        try {
            // 실제 구현에서는 Metrics Server API 호출
            // 현재는 모의 데이터 반환
            metrics.put("cpu", "100m");
            metrics.put("memory", "512Mi");
            metrics.put("gpu", "0");
            
            log.debug("Retrieved pod metrics for {}/{}: {}", namespace, podName, metrics);
            
        } catch (Exception e) {
            log.error("Error fetching pod metrics for {}/{}: {}", namespace, podName, e.getMessage());
            metrics = getDefaultPodMetrics();
        }
        
        return metrics;
    }

    /**
     * Node의 현재 메트릭 조회
     * @param nodeName 노드 이름
     * @return 리소스 사용량 맵 (cpu, memory, gpu, storage)
     */
    public Map<String, String> getNodeMetrics(String nodeName) {
        Map<String, String> metrics = new HashMap<>();
        
        try {
            // 실제 구현에서는 Metrics Server API 호출
            // 현재는 모의 데이터 반환
            metrics.put("cpu", "2000m");
            metrics.put("memory", "4Gi");
            metrics.put("gpu", "0");
            metrics.put("storage", "50Gi");
            
            log.debug("Retrieved node metrics for {}: {}", nodeName, metrics);
            
        } catch (Exception e) {
            log.error("Error fetching node metrics for {}: {}", nodeName, e.getMessage());
            metrics = getDefaultNodeMetrics();
        }
        
        return metrics;
    }

    /**
     * 메트릭 서버 헬스 체크
     * @return 메트릭 서버 사용 가능 여부
     */
    public boolean isMetricsServerAvailable() {
        try {
            // 실제 구현에서는 메트릭 서버 상태 확인
            // 현재는 항상 true 반환
            return true;
        } catch (Exception e) {
            log.debug("Metrics server is not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 기본 Pod 메트릭 반환 (메트릭 서버 사용 불가 시)
     */
    private Map<String, String> getDefaultPodMetrics() {
        Map<String, String> metrics = new HashMap<>();
        metrics.put("cpu", "0m");
        metrics.put("memory", "0Mi");
        metrics.put("gpu", "0");
        metrics.put("storage", "0Gi");
        return metrics;
    }

    /**
     * 기본 Node 메트릭 반환 (메트릭 서버 사용 불가 시)
     */
    private Map<String, String> getDefaultNodeMetrics() {
        Map<String, String> metrics = new HashMap<>();
        metrics.put("cpu", "0m");
        metrics.put("memory", "0Mi");
        metrics.put("gpu", "0");
        metrics.put("storage", "0Gi");
        return metrics;
    }

    /**
     * 메트릭 캐시 (성능 향상을 위한 간단한 캐시)
     */
    private final Map<String, CachedMetric> metricsCache = new HashMap<>();
    private static final long CACHE_TTL_MS = 30000; // 30초

    private static class CachedMetric {
        final Map<String, String> metrics;
        final long timestamp;

        CachedMetric(Map<String, String> metrics) {
            this.metrics = metrics;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    /**
     * 캐시된 Pod 메트릭 조회
     */
    public Map<String, String> getCachedPodMetrics(String namespace, String podName) {
        String key = namespace + "/" + podName;
        CachedMetric cached = metricsCache.get(key);
        
        if (cached != null && !cached.isExpired()) {
            return cached.metrics;
        }
        
        Map<String, String> metrics = getPodMetrics(namespace, podName);
        metricsCache.put(key, new CachedMetric(metrics));
        
        return metrics;
    }

    /**
     * 캐시된 Node 메트릭 조회
     */
    public Map<String, String> getCachedNodeMetrics(String nodeName) {
        CachedMetric cached = metricsCache.get(nodeName);
        
        if (cached != null && !cached.isExpired()) {
            return cached.metrics;
        }
        
        Map<String, String> metrics = getNodeMetrics(nodeName);
        metricsCache.put(nodeName, new CachedMetric(metrics));
        
        return metrics;
    }

    /**
     * 메트릭 캐시 정리
     */
    public void clearExpiredCache() {
        metricsCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        log.debug("Cleared expired cache entries. Current cache size: {}", metricsCache.size());
    }

    /**
     * 전체 캐시 정리
     */
    public void clearAllCache() {
        metricsCache.clear();
        log.debug("Cleared all cache entries");
    }
}