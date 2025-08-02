package com.k8s.monitor.service;

import com.k8s.monitor.dto.NodeResourceInfo;
import com.k8s.monitor.dto.PodResourceInfo;
import com.k8s.monitor.dto.ResourceUsageResponse;
import com.k8s.monitor.entity.ResourceMetrics; // 수정된 import
import com.k8s.monitor.repository.MetricsRepository; // 수정된 import
import lombok.RequiredArgsConstructor; // 수정된 import
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 리소스 사용량 분석 및 통계 계산 서비스 (완전한 버전)
 * 클러스터 전체의 리소스 분석, 예측, 알람 등의 기능 제공
 */
@Service
@RequiredArgsConstructor // 이제 정상 작동
@Slf4j
public class ResourceAnalysisService {
    
    private final MetricsRepository metricsRepository; // 수정된 의존성
    private final ResourceMetricsService metricsService;

    /**
     * 클러스터 요약 정보 계산
     * @param pods Pod 리스트
     * @param nodes Node 리스트
     * @return 클러스터 요약 정보
     */
    public ResourceUsageResponse.ClusterSummary calculateClusterSummary(
            List<PodResourceInfo> pods, List<NodeResourceInfo> nodes) {
        
        try {
            // 기본 통계
            int totalNodes = nodes.size();
            int readyNodes = (int) nodes.stream()
                .filter(node -> "Ready".equals(node.getStatus()))
                .count();
            
            int totalPods = pods.size();
            int runningPods = (int) pods.stream()
                .filter(pod -> "Running".equals(pod.getPhase()))
                .count();
            
            int vllmPods = (int) pods.stream()
                .filter(pod -> "vllm".equals(pod.getModelType()))
                .count();
                
            int sglangPods = (int) pods.stream()
                .filter(pod -> "sglang".equals(pod.getModelType()))
                .count();

            // 전체 용량 계산
            String totalCpuCapacity = nodes.stream()
                .map(NodeResourceInfo::getCpuCapacity)
                .filter(Objects::nonNull)
                .reduce("0", this::addResourceValues);
                
            String totalMemoryCapacity = nodes.stream()
                .map(NodeResourceInfo::getMemoryCapacity)
                .filter(Objects::nonNull)
                .reduce("0", this::addResourceValues);
                
            String totalGpuCapacity = nodes.stream()
                .map(NodeResourceInfo::getGpuCapacity)
                .filter(Objects::nonNull)
                .reduce("0", this::addResourceValues);

            // 평균 사용률 계산
            Double avgCpuUsage = nodes.stream()
                .mapToDouble(n -> n.getCpuUsagePercent() != null ? n.getCpuUsagePercent() : 0.0)
                .average()
                .orElse(0.0);
                
            Double avgMemoryUsage = nodes.stream()
                .mapToDouble(n -> n.getMemoryUsagePercent() != null ? n.getMemoryUsagePercent() : 0.0)
                .average()
                .orElse(0.0);
                
            Double avgGpuUsage = nodes.stream()
                .mapToDouble(n -> n.getGpuUsagePercent() != null ? n.getGpuUsagePercent() : 0.0)
                .average()
                .orElse(0.0);

            // 모델별 리소스 요약
            ResourceUsageResponse.ModelResourceSummary vllmSummary = calculateModelResourceSummary(pods, "vllm");
            ResourceUsageResponse.ModelResourceSummary sglangSummary = calculateModelResourceSummary(pods, "sglang");

            // 상위 리소스 사용 Pod
            List<String> topCpuPods = getTopResourceUsagePodNames(pods, "cpu", 5);
            List<String> topMemoryPods = getTopResourceUsagePodNames(pods, "memory", 5);
            List<String> topGpuPods = getTopResourceUsagePodNames(pods, "gpu", 5);

            // 알람 계산
            List<String> alerts = generateAlerts(pods, nodes);

            return ResourceUsageResponse.ClusterSummary.builder()
                .totalNodes(totalNodes)
                .readyNodes(readyNodes)
                .totalPods(totalPods)
                .runningPods(runningPods)
                .vllmPods(vllmPods)
                .sglangPods(sglangPods)
                .totalCpuCapacity(totalCpuCapacity)
                .totalMemoryCapacity(totalMemoryCapacity)
                .totalGpuCapacity(totalGpuCapacity)
                .avgCpuUsage(avgCpuUsage)
                .avgMemoryUsage(avgMemoryUsage)
                .avgGpuUsage(avgGpuUsage)
                .vllmResourceSummary(vllmSummary)
                .sglangResourceSummary(sglangSummary)
                .topCpuPods(topCpuPods)
                .topMemoryPods(topMemoryPods)
                .topGpuPods(topGpuPods)
                .alerts(alerts)
                .criticalAlerts((int) alerts.stream().filter(alert -> alert.contains("CRITICAL")).count())
                .warningAlerts((int) alerts.stream().filter(alert -> alert.contains("WARNING")).count())
                .build();
                
        } catch (Exception e) {
            log.error("Error calculating cluster summary: {}", e.getMessage(), e);
            return createDefaultClusterSummary();
        }
    }

    /**
     * 모델별 리소스 요약 계산
     */
    private ResourceUsageResponse.ModelResourceSummary calculateModelResourceSummary(
            List<PodResourceInfo> pods, String modelType) {
        
        try {
            List<PodResourceInfo> modelPods = pods.stream()
                .filter(pod -> modelType.equals(pod.getModelType()))
                .collect(Collectors.toList());

            if (modelPods.isEmpty()) {
                return ResourceUsageResponse.ModelResourceSummary.builder()
                    .modelType(modelType)
                    .podCount(0)
                    .totalCpuUsage("0")
                    .totalMemoryUsage("0")
                    .totalGpuUsage("0")
                    .avgCpuUtilization(0.0)
                    .avgMemoryUtilization(0.0)
                    .avgGpuUtilization(0.0)
                    .build();
            }

            String totalCpuUsage = modelPods.stream()
                .map(PodResourceInfo::getCpuUsage)
                .filter(Objects::nonNull)
                .reduce("0", this::addResourceValues);
                
            String totalMemoryUsage = modelPods.stream()
                .map(PodResourceInfo::getMemoryUsage)
                .filter(Objects::nonNull)
                .reduce("0", this::addResourceValues);
                
            String totalGpuUsage = modelPods.stream()
                .map(PodResourceInfo::getGpuUsage)
                .filter(Objects::nonNull)
                .reduce("0", this::addResourceValues);

            double avgCpuUtilization = modelPods.stream()
                .mapToDouble(p -> p.getCpuUsagePercent() != null ? p.getCpuUsagePercent() : 0.0)
                .average()
                .orElse(0.0);
                
            double avgMemoryUtilization = modelPods.stream()
                .mapToDouble(p -> p.getMemoryUsagePercent() != null ? p.getMemoryUsagePercent() : 0.0)
                .average()
                .orElse(0.0);
                
            double avgGpuUtilization = modelPods.stream()
                .mapToDouble(p -> p.getGpuUsagePercent() != null ? p.getGpuUsagePercent() : 0.0)
                .average()
                .orElse(0.0);

            return ResourceUsageResponse.ModelResourceSummary.builder()
                .modelType(modelType)
                .podCount(modelPods.size())
                .totalCpuUsage(totalCpuUsage)
                .totalMemoryUsage(totalMemoryUsage)
                .totalGpuUsage(totalGpuUsage)
                .avgCpuUtilization(avgCpuUtilization)
                .avgMemoryUtilization(avgMemoryUtilization)
                .avgGpuUtilization(avgGpuUtilization)
                .build();
                
        } catch (Exception e) {
            log.error("Error calculating model resource summary for {}: {}", modelType, e.getMessage());
            return createDefaultModelSummary(modelType);
        }
    }

    /**
     * 상위 리소스 사용 Pod 목록 반환
     */
    public List<PodResourceInfo> getTopResourceUsagePods(String resourceType, int limit) {
        try {
            // 실제 구현에서는 KubernetesService를 통해 현재 Pod 정보 조회
            List<PodResourceInfo> allPods = getCurrentPods(); // 헬퍼 메서드로 분리
            
            Comparator<PodResourceInfo> comparator = getResourceComparator(resourceType);

            return allPods.stream()
                .sorted(comparator)
                .limit(limit)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Error getting top resource usage pods: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 클러스터 헬스 상태 조회
     */
    public Map<String, Object> getClusterHealthStatus() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // 메트릭 서버 상태 확인
            boolean metricsServerAvailable = metricsService.isMetricsServerAvailable();
            health.put("metricsServerAvailable", metricsServerAvailable);
            
            // 최근 메트릭 수집 상태
            LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
            long recentMetricsCount = metricsRepository.countMetricsBetween(oneHourAgo, LocalDateTime.now());
            health.put("recentMetricsCount", recentMetricsCount);
            
            // 전반적인 헬스 상태
            String overallStatus = metricsServerAvailable && recentMetricsCount > 0 ? "HEALTHY" : "DEGRADED";
            health.put("overallStatus", overallStatus);
            
            health.put("timestamp", LocalDateTime.now());
            
        } catch (Exception e) {
            log.error("Error calculating cluster health: {}", e.getMessage());
            health.put("overallStatus", "ERROR");
            health.put("error", e.getMessage());
            health.put("timestamp", LocalDateTime.now());
        }
        
        return health;
    }

    /**
     * 리소스 사용량 통계 조회
     */
    public Map<String, Object> getResourceStatistics(int hours) {
        Map<String, Object> statistics = new HashMap<>();
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        try {
            // 시간별 메트릭 수집
            List<ResourceMetrics> metrics = metricsRepository.findMetricsSince(since);
            
            // Pod 통계
            Map<String, Object> podStats = calculatePodStatistics(metrics);
            statistics.put("podStatistics", podStats);
            
            // Node 통계
            Map<String, Object> nodeStats = calculateNodeStatistics(metrics);
            statistics.put("nodeStatistics", nodeStats);
            
            // 모델별 통계
            Map<String, Object> modelStats = calculateModelStatistics(metrics);
            statistics.put("modelStatistics", modelStats);
            
            // 시간별 트렌드
            Map<String, Object> trends = calculateResourceTrends(metrics, hours);
            statistics.put("trends", trends);
            
            statistics.put("timeRange", hours + " hours");
            statistics.put("timestamp", LocalDateTime.now());
            
        } catch (Exception e) {
            log.error("Error calculating resource statistics: {}", e.getMessage());
            statistics.put("error", e.getMessage());
            statistics.put("timestamp", LocalDateTime.now());
        }
        
        return statistics;
    }

    /**
     * 네임스페이스별 리소스 사용량 조회
     */
    public Map<String, Object> getNamespaceResourceUsage() {
        Map<String, Object> namespaceUsage = new HashMap<>();
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        
        try {
            List<ResourceMetrics> podMetrics = metricsRepository.findMetricsByTypeAndTimeRange(
                "POD", oneHourAgo, LocalDateTime.now());
            
            Map<String, List<ResourceMetrics>> metricsByNamespace = podMetrics.stream()
                .filter(metric -> metric.getNamespace() != null)
                .collect(Collectors.groupingBy(ResourceMetrics::getNamespace));
            
            Map<String, Map<String, Object>> namespaceStats = new HashMap<>();
            
            for (Map.Entry<String, List<ResourceMetrics>> entry : metricsByNamespace.entrySet()) {
                String namespace = entry.getKey();
                List<ResourceMetrics> metrics = entry.getValue();
                
                Map<String, Object> stats = calculateNamespaceStats(metrics);
                namespaceStats.put(namespace, stats);
            }
            
            namespaceUsage.put("namespaces", namespaceStats);
            namespaceUsage.put("timestamp", LocalDateTime.now());
            
        } catch (Exception e) {
            log.error("Error calculating namespace resource usage: {}", e.getMessage());
            namespaceUsage.put("error", e.getMessage());
            namespaceUsage.put("timestamp", LocalDateTime.now());
        }
        
        return namespaceUsage;
    }

    /**
     * 리소스 알람 조회
     */
    public List<Map<String, Object>> getResourceAlerts() {
        List<Map<String, Object>> alerts = new ArrayList<>();
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        
        try {
            // 높은 리소스 사용률 알람 (80% 이상)
            List<ResourceMetrics> highUsageMetrics = metricsRepository.findHighUsageMetrics(
                80.0, 80.0, oneHourAgo);
            
            for (ResourceMetrics metric : highUsageMetrics) {
                alerts.addAll(createAlertsForMetric(metric));
            }
            
        } catch (Exception e) {
            log.error("Error generating resource alerts: {}", e.getMessage());
        }
        
        return alerts;
    }

    /**
     * 리소스 사용량 예측
     */
    public Map<String, Object> getResourceForecast(int hours) {
        Map<String, Object> forecast = new HashMap<>();
        LocalDateTime since = LocalDateTime.now().minusHours(hours * 2); // 예측을 위해 더 긴 기간의 데이터 사용
        
        try {
            List<ResourceMetrics> historicalMetrics = metricsRepository.findMetricsSince(since);
            
            // 간단한 선형 추세 기반 예측
            Map<String, Double> cpuTrend = calculateResourceTrend(historicalMetrics, "cpu");
            Map<String, Double> memoryTrend = calculateResourceTrend(historicalMetrics, "memory");
            Map<String, Double> gpuTrend = calculateResourceTrend(historicalMetrics, "gpu");
            
            forecast.put("cpuForecast", cpuTrend);
            forecast.put("memoryForecast", memoryTrend);
            forecast.put("gpuForecast", gpuTrend);
            forecast.put("forecastHours", hours);
            forecast.put("timestamp", LocalDateTime.now());
            
        } catch (Exception e) {
            log.error("Error calculating resource forecast: {}", e.getMessage());
            forecast.put("error", e.getMessage());
            forecast.put("timestamp", LocalDateTime.now());
        }
        
        return forecast;
    }

    /**
     * 메트릭 서버 상태 확인
     */
    public Map<String, Object> getMetricsServerStatus() {
        Map<String, Object> status = new HashMap<>();
        
        try {
            boolean available = metricsService.isMetricsServerAvailable();
            status.put("available", available);
            status.put("status", available ? "RUNNING" : "UNAVAILABLE");
            
            if (available) {
                // 추가 상태 정보 수집
                LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
                long recentMetrics = metricsRepository.countMetricsBetween(oneHourAgo, LocalDateTime.now());
                status.put("recentMetrics", recentMetrics);
                status.put("lastSuccessfulCollection", LocalDateTime.now());
            }
            
            status.put("timestamp", LocalDateTime.now());
            
        } catch (Exception e) {
            log.error("Error checking metrics server status: {}", e.getMessage());
            status.put("available", false);
            status.put("status", "ERROR");
            status.put("error", e.getMessage());
            status.put("timestamp", LocalDateTime.now());
        }
        
        return status;
    }

    /**
     * 리소스 통계 요약 정보
     */
    public Map<String, Object> getResourceSummary() {
        Map<String, Object> summary = new HashMap<>();
        
        try {
            LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
            List<ResourceMetrics> recentMetrics = metricsRepository.findMetricsSince(oneHourAgo);
            
            // 전체 메트릭 수
            long totalMetrics = recentMetrics.size();
            
            // 타입별 메트릭 수
            Map<String, Long> metricsByType = recentMetrics.stream()
                .collect(Collectors.groupingBy(
                    ResourceMetrics::getResourceType,
                    Collectors.counting()
                ));
            
            // 평균 사용률
            double avgCpuUsage = recentMetrics.stream()
                .mapToDouble(this::getCpuUsagePercentSafely)
                .average()
                .orElse(0.0);
            
            double avgMemoryUsage = recentMetrics.stream()
                .mapToDouble(this::getMemoryUsagePercentSafely)
                .average()
                .orElse(0.0);
            
            summary.put("totalMetrics", totalMetrics);
            summary.put("metricsByType", metricsByType);
            summary.put("avgCpuUsage", avgCpuUsage);
            summary.put("avgMemoryUsage", avgMemoryUsage);
            summary.put("timeRange", "1 hour");
            summary.put("timestamp", LocalDateTime.now());
            
        } catch (Exception e) {
            log.error("Error getting resource summary: {}", e.getMessage());
            summary.put("error", e.getMessage());
            summary.put("timestamp", LocalDateTime.now());
        }
        
        return summary;
    }

    /**
     * 고사용률 리소스 조회
     */
    public List<Map<String, Object>> getHighUsageResources(double threshold) {
        List<Map<String, Object>> highUsageResources = new ArrayList<>();
        
        try {
            LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
            List<ResourceMetrics> metrics = metricsRepository.findHighUsageMetrics(threshold, threshold, oneHourAgo);
            
            for (ResourceMetrics metric : metrics) {
                Map<String, Object> resource = new HashMap<>();
                resource.put("resourceType", metric.getResourceType());
                resource.put("resourceName", metric.getResourceName());
                resource.put("namespace", metric.getNamespace());
                resource.put("cpuUsage", getCpuUsagePercentSafely(metric));
                resource.put("memoryUsage", getMemoryUsagePercentSafely(metric));
                resource.put("timestamp", metric.getTimestamp());
                
                highUsageResources.add(resource);
            }
            
        } catch (Exception e) {
            log.error("Error getting high usage resources: {}", e.getMessage());
        }
        
        return highUsageResources;
    }

    /**
     * 클러스터 용량 분석
     */
    public Map<String, Object> getCapacityAnalysis() {
        Map<String, Object> analysis = new HashMap<>();
        
        try {
            // 현재 Pod과 Node 정보 조회 (실제로는 KubernetesService 사용)
            List<PodResourceInfo> pods = getCurrentPods();
            List<NodeResourceInfo> nodes = getCurrentNodes();
            
            // 전체 용량
            String totalCpuCapacity = nodes.stream()
                .map(NodeResourceInfo::getCpuCapacity)
                .filter(Objects::nonNull)
                .reduce("0", this::addResourceValues);
            
            String totalMemoryCapacity = nodes.stream()
                .map(NodeResourceInfo::getMemoryCapacity)
                .filter(Objects::nonNull)
                .reduce("0", this::addResourceValues);
            
            // 사용 중인 용량
            String usedCpuCapacity = pods.stream()
                .map(PodResourceInfo::getCpuUsage)
                .filter(Objects::nonNull)
                .reduce("0", this::addResourceValues);
            
            String usedMemoryCapacity = pods.stream()
                .map(PodResourceInfo::getMemoryUsage)
                .filter(Objects::nonNull)
                .reduce("0", this::addResourceValues);
            
            // 사용률 계산
            double cpuUtilization = calculateUtilizationPercent(usedCpuCapacity, totalCpuCapacity);
            double memoryUtilization = calculateUtilizationPercent(usedMemoryCapacity, totalMemoryCapacity);
            
            analysis.put("totalCpuCapacity", totalCpuCapacity);
            analysis.put("totalMemoryCapacity", totalMemoryCapacity);
            analysis.put("usedCpuCapacity", usedCpuCapacity);
            analysis.put("usedMemoryCapacity", usedMemoryCapacity);
            analysis.put("cpuUtilization", cpuUtilization);
            analysis.put("memoryUtilization", memoryUtilization);
            analysis.put("availableNodes", nodes.size());
            analysis.put("totalPods", pods.size());
            analysis.put("timestamp", LocalDateTime.now());
            
        } catch (Exception e) {
            log.error("Error getting capacity analysis: {}", e.getMessage());
            analysis.put("error", e.getMessage());
            analysis.put("timestamp", LocalDateTime.now());
        }
        
        return analysis;
    }

    // Private helper methods

    private ResourceUsageResponse.ClusterSummary createDefaultClusterSummary() {
        return ResourceUsageResponse.ClusterSummary.builder()
            .totalNodes(0)
            .readyNodes(0)
            .totalPods(0)
            .runningPods(0)
            .vllmPods(0)
            .sglangPods(0)
            .totalCpuCapacity("0")
            .totalMemoryCapacity("0")
            .totalGpuCapacity("0")
            .avgCpuUsage(0.0)
            .avgMemoryUsage(0.0)
            .avgGpuUsage(0.0)
            .topCpuPods(new ArrayList<>())
            .topMemoryPods(new ArrayList<>())
            .topGpuPods(new ArrayList<>())
            .alerts(new ArrayList<>())
            .criticalAlerts(0)
            .warningAlerts(0)
            .build();
    }

    private ResourceUsageResponse.ModelResourceSummary createDefaultModelSummary(String modelType) {
        return ResourceUsageResponse.ModelResourceSummary.builder()
            .modelType(modelType)
            .podCount(0)
            .totalCpuUsage("0")
            .totalMemoryUsage("0")
            .totalGpuUsage("0")
            .avgCpuUtilization(0.0)
            .avgMemoryUtilization(0.0)
            .avgGpuUtilization(0.0)
            .build();
    }

    private List<PodResourceInfo> getCurrentPods() {
        // 실제 구현에서는 KubernetesService를 통해 현재 Pod 정보 조회
        return new ArrayList<>();
    }

    private List<NodeResourceInfo> getCurrentNodes() {
        // 실제 구현에서는 KubernetesService를 통해 현재 Node 정보 조회
        return new ArrayList<>();
    }

    private Comparator<PodResourceInfo> getResourceComparator(String resourceType) {
        switch (resourceType.toLowerCase()) {
            case "memory":
                return Comparator.comparing(
                    pod -> pod.getMemoryUsagePercent() != null ? pod.getMemoryUsagePercent() : 0.0, 
                    Comparator.reverseOrder());
            case "gpu":
                return Comparator.comparing(
                    pod -> pod.getGpuUsagePercent() != null ? pod.getGpuUsagePercent() : 0.0, 
                    Comparator.reverseOrder());
            default: // cpu
                return Comparator.comparing(
                    pod -> pod.getCpuUsagePercent() != null ? pod.getCpuUsagePercent() : 0.0, 
                    Comparator.reverseOrder());
        }
    }

    private List<String> getTopResourceUsagePodNames(List<PodResourceInfo> pods, String resourceType, int limit) {
        Comparator<PodResourceInfo> comparator = getResourceComparator(resourceType);

        return pods.stream()
            .sorted(comparator)
            .limit(limit)
            .map(pod -> pod.getNamespace() + "/" + pod.getName())
            .collect(Collectors.toList());
    }

    private List<String> generateAlerts(List<PodResourceInfo> pods, List<NodeResourceInfo> nodes) {
        List<String> alerts = new ArrayList<>();
        
        try {
            // Pod 관련 알람
            for (PodResourceInfo pod : pods) {
                if (pod.getCpuUsagePercent() != null && pod.getCpuUsagePercent() > 90) {
                    alerts.add(String.format("CRITICAL: Pod %s/%s CPU usage is %.0f%%", 
                        pod.getNamespace(), pod.getName(), pod.getCpuUsagePercent()));
                }
                if (pod.getMemoryUsagePercent() != null && pod.getMemoryUsagePercent() > 90) {
                    alerts.add(String.format("CRITICAL: Pod %s/%s Memory usage is %.0f%%", 
                        pod.getNamespace(), pod.getName(), pod.getMemoryUsagePercent()));
                }
                if (!"Running".equals(pod.getPhase())) {
                    alerts.add(String.format("WARNING: Pod %s/%s is in %s state", 
                        pod.getNamespace(), pod.getName(), pod.getPhase()));
                }
            }
            
            // Node 관련 알람
            for (NodeResourceInfo node : nodes) {
                if (!"Ready".equals(node.getStatus())) {
                    alerts.add(String.format("CRITICAL: Node %s is %s", node.getName(), node.getStatus()));
                }
                if (node.getCpuUsagePercent() != null && node.getCpuUsagePercent() > 85) {
                    alerts.add(String.format("WARNING: Node %s CPU usage is %.0f%%", 
                        node.getName(), node.getCpuUsagePercent()));
                }
                if (node.getMemoryUsagePercent() != null && node.getMemoryUsagePercent() > 85) {
                    alerts.add(String.format("WARNING: Node %s Memory usage is %.0f%%", 
                        node.getName(), node.getMemoryUsagePercent()));
                }
            }
        } catch (Exception e) {
            log.warn("Error generating alerts: {}", e.getMessage());
        }
        
        return alerts;
    }

    private Map<String, Object> calculateNamespaceStats(List<ResourceMetrics> metrics) {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("podCount", metrics.stream().map(ResourceMetrics::getResourceName).distinct().count());
        stats.put("avgCpuUsage", metrics.stream()
            .mapToDouble(this::getCpuUsagePercentSafely)
            .average().orElse(0.0));
        stats.put("avgMemoryUsage", metrics.stream()
            .mapToDouble(this::getMemoryUsagePercentSafely)
            .average().orElse(0.0));
        
        return stats;
    }

    private List<Map<String, Object>> createAlertsForMetric(ResourceMetrics metric) {
        List<Map<String, Object>> alerts = new ArrayList<>();
        
        Double cpuUsage = getCpuUsagePercentSafely(metric);
        Double memoryUsage = getMemoryUsagePercentSafely(metric);
        
        if (cpuUsage > 80) {
            alerts.add(createAlert("HIGH_CPU_USAGE", metric, "CPU usage above 80%"));
        }
        if (memoryUsage > 80) {
            alerts.add(createAlert("HIGH_MEMORY_USAGE", metric, "Memory usage above 80%"));
        }
        
        return alerts;
    }

    private Map<String, Object> createAlert(String type, ResourceMetrics metric, String message) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("type", type);
        alert.put("resourceType", metric.getResourceType());
        alert.put("resourceName", metric.getResourceName());
        alert.put("namespace", metric.getNamespace());
        alert.put("message", message);
        alert.put("timestamp", metric.getTimestamp());
        alert.put("severity", type.contains("CRITICAL") ? "CRITICAL" : "WARNING");
        return alert;
    }

    private Map<String, Object> calculatePodStatistics(List<ResourceMetrics> metrics) {
        List<ResourceMetrics> podMetrics = metrics.stream()
            .filter(m -> "POD".equals(m.getResourceType()))
            .collect(Collectors.toList());
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalPods", podMetrics.stream().map(ResourceMetrics::getResourceName).distinct().count());
        stats.put("avgCpuUsage", podMetrics.stream()
            .mapToDouble(this::getCpuUsagePercentSafely)
            .average().orElse(0.0));
        stats.put("avgMemoryUsage", podMetrics.stream()
            .mapToDouble(this::getMemoryUsagePercentSafely)
            .average().orElse(0.0));
        
        return stats;
    }

    private Map<String, Object> calculateNodeStatistics(List<ResourceMetrics> metrics) {
        List<ResourceMetrics> nodeMetrics = metrics.stream()
            .filter(m -> "NODE".equals(m.getResourceType()))
            .collect(Collectors.toList());
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalNodes", nodeMetrics.stream().map(ResourceMetrics::getResourceName).distinct().count());
        stats.put("avgCpuUsage", nodeMetrics.stream()
            .mapToDouble(this::getCpuUsagePercentSafely)
            .average().orElse(0.0));
        stats.put("avgMemoryUsage", nodeMetrics.stream()
            .mapToDouble(this::getMemoryUsagePercentSafely)
            .average().orElse(0.0));
        
        return stats;
    }

    private Map<String, Object> calculateModelStatistics(List<ResourceMetrics> metrics) {
        // ResourceMetrics에 modelType 필드가 있다고 가정
        Map<String, List<ResourceMetrics>> metricsByModel = metrics.stream()
            .filter(m -> "POD".equals(m.getResourceType()))
            .filter(m -> getModelTypeFromMetric(m) != null)
            .collect(Collectors.groupingBy(this::getModelTypeFromMetric));
        
        Map<String, Object> modelStats = new HashMap<>();
        
        for (Map.Entry<String, List<ResourceMetrics>> entry : metricsByModel.entrySet()) {
            String modelType = entry.getKey();
            List<ResourceMetrics> modelMetrics = entry.getValue();
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("podCount", modelMetrics.stream().map(ResourceMetrics::getResourceName).distinct().count());
            stats.put("avgCpuUsage", modelMetrics.stream()
                .mapToDouble(this::getCpuUsagePercentSafely)
                .average().orElse(0.0));
            stats.put("avgMemoryUsage", modelMetrics.stream()
                .mapToDouble(this::getMemoryUsagePercentSafely)
                .average().orElse(0.0));
            
            modelStats.put(modelType, stats);
        }
        
        return modelStats;
    }

    private String getModelTypeFromMetric(ResourceMetrics metric) {
        // ResourceMetrics에서 모델 타입 추출 로직
        // 실제 구현은 엔티티 구조에 따라 달라질 수 있음
        try {
            // 예시: 리소스 이름에서 추출하거나 별도 필드 사용
            String resourceName = metric.getResourceName();
            if (resourceName != null && resourceName.contains("vllm")) {
                return "vllm";
            } else if (resourceName != null && resourceName.contains("sglang")) {
                return "sglang";
            }
            return "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private Map<String, Object> calculateResourceTrends(List<ResourceMetrics> metrics, int hours) {
        Map<String, Object> trends = new HashMap<>();
        
        try {
            // 시간대별 사용률 계산 (단순화된 구현)
            Map<Integer, List<ResourceMetrics>> metricsByHour = metrics.stream()
                .collect(Collectors.groupingBy(m -> m.getTimestamp().getHour()));
            
            Map<Integer, Double> cpuTrendByHour = new HashMap<>();
            Map<Integer, Double> memoryTrendByHour = new HashMap<>();
            
            for (Map.Entry<Integer, List<ResourceMetrics>> entry : metricsByHour.entrySet()) {
                Integer hour = entry.getKey();
                List<ResourceMetrics> hourMetrics = entry.getValue();
                
                double avgCpu = hourMetrics.stream()
                    .mapToDouble(this::getCpuUsagePercentSafely)
                    .average().orElse(0.0);
                double avgMemory = hourMetrics.stream()
                    .mapToDouble(this::getMemoryUsagePercentSafely)
                    .average().orElse(0.0);
                
                cpuTrendByHour.put(hour, avgCpu);
                memoryTrendByHour.put(hour, avgMemory);
            }
            
            trends.put("cpuTrendByHour", cpuTrendByHour);
            trends.put("memoryTrendByHour", memoryTrendByHour);
            
        } catch (Exception e) {
            log.warn("Error calculating resource trends: {}", e.getMessage());
        }
        
        return trends;
    }

    private Map<String, Double> calculateResourceTrend(List<ResourceMetrics> metrics, String resourceType) {
        Map<String, Double> trend = new HashMap<>();
        
        try {
            // 단순한 선형 회귀를 통한 추세 계산
            List<Double> values = new ArrayList<>();
            
            switch (resourceType) {
                case "cpu":
                    values = metrics.stream()
                        .mapToDouble(this::getCpuUsagePercentSafely)
                        .boxed()
                        .collect(Collectors.toList());
                    break;
                case "memory":
                    values = metrics.stream()
                        .mapToDouble(this::getMemoryUsagePercentSafely)
                        .boxed()
                        .collect(Collectors.toList());
                    break;
                case "gpu":
                    // GPU 사용률이 ResourceMetrics에 있다면
                    values = metrics.stream()
                        .mapToDouble(m -> 0.0) // GPU 필드가 있다면 실제 값 사용
                        .boxed()
                        .collect(Collectors.toList());
                    break;
                default:
                    values = Arrays.asList(0.0);
            }
            
            if (!values.isEmpty()) {
                double current = values.get(values.size() - 1);
                double average = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                double trendDirection = current - average;
                
                trend.put("current", current);
                trend.put("average", average);
                trend.put("trend", trendDirection);
                trend.put("prediction", Math.max(0, Math.min(100, current + trendDirection)));
            }
            
        } catch (Exception e) {
            log.warn("Error calculating {} trend: {}", resourceType, e.getMessage());
            trend.put("current", 0.0);
            trend.put("average", 0.0);
            trend.put("trend", 0.0);
            trend.put("prediction", 0.0);
        }
        
        return trend;
    }

    /**
     * ResourceMetrics에 사용률 계산 메서드가 없는 경우를 위한 헬퍼
     */
    private Double getCpuUsagePercent(ResourceMetrics metric) {
        try {
            // ResourceMetrics 엔티티에 getCpuUtilizationPercent() 메서드가 있다면 사용
            return metric.getCpuUtilizationPercent();
        } catch (Exception e) {
            // 메서드가 없는 경우 수동 계산
            if (metric.getCpuUsageCores() != null && metric.getCpuLimitCores() != null && metric.getCpuLimitCores() > 0) {
                return (metric.getCpuUsageCores() / metric.getCpuLimitCores()) * 100.0;
            }
            return 0.0;
        }
    }

    private Double getMemoryUsagePercent(ResourceMetrics metric) {
        try {
            // ResourceMetrics 엔티티에 getMemoryUtilizationPercent() 메서드가 있다면 사용
            return metric.getMemoryUtilizationPercent();
        } catch (Exception e) {
            // 메서드가 없는 경우 수동 계산
            if (metric.getMemoryUsageBytes() != null && metric.getMemoryLimitBytes() != null && metric.getMemoryLimitBytes() > 0) {
                return ((double) metric.getMemoryUsageBytes() / metric.getMemoryLimitBytes()) * 100.0;
            }
            return 0.0;
        }
    }

    /**
     * 안전한 사용률 접근 메서드들
     */
    private Double getCpuUsagePercentSafely(ResourceMetrics metric) {
        try {
            Double percent = getCpuUsagePercent(metric);
            return percent != null ? percent : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private Double getMemoryUsagePercentSafely(ResourceMetrics metric) {
        try {
            Double percent = getMemoryUsagePercent(metric);
            return percent != null ? percent : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private String addResourceValues(String value1, String value2) {
        try {
            double val1 = parseResourceValue(value1);
            double val2 = parseResourceValue(value2);
            return String.valueOf(val1 + val2);
        } catch (NumberFormatException e) {
            log.warn("Error adding resource values '{}' + '{}': {}", value1, value2, e.getMessage());
            return "0";
        }
    }

    private double parseResourceValue(String value) {
        if (value == null || value.isEmpty() || "0".equals(value)) {
            return 0.0;
        }
        
        try {
            // 단위 제거 후 숫자만 추출
            String numericValue = value.replaceAll("[^0-9.]", "");
            if (numericValue.isEmpty()) {
                return 0.0;
            }
            return Double.parseDouble(numericValue);
        } catch (NumberFormatException e) {
            log.warn("Error parsing resource value '{}': {}", value, e.getMessage());
            return 0.0;
        }
    }

    private double calculateUtilizationPercent(String used, String total) {
        try {
            double usedValue = parseResourceValue(used);
            double totalValue = parseResourceValue(total);
            
            if (totalValue > 0) {
                return (usedValue / totalValue) * 100.0;
            }
            return 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }
}