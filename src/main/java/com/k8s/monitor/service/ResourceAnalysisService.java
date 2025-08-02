package com.k8s.monitor.service;

import com.k8s.monitor.dto.NodeResourceInfo;
import com.k8s.monitor.dto.PodResourceInfo;
import com.k8s.monitor.dto.ResourceUsageResponse;
import com.k8s.monitor.model.ResourceMetrics;
import com.k8s.monitor.repository.MetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 리소스 사용량 분석 및 통계 계산 서비스
 * 클러스터 전체의 리소스 분석, 예측, 알람 등의 기능 제공
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceAnalysisService {
    
    private final MetricsRepository metricsRepository;
    private final ResourceMetricsService metricsService;

    /**
     * 클러스터 요약 정보 계산
     * @param pods Pod 리스트
     * @param nodes Node 리스트
     * @return 클러스터 요약 정보
     */
    public ResourceUsageResponse.ClusterSummary calculateClusterSummary(
            List<PodResourceInfo> pods, List<NodeResourceInfo> nodes) {
        
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
            .reduce("0", this::addResourceValues);
            
        String totalMemoryCapacity = nodes.stream()
            .map(NodeResourceInfo::getMemoryCapacity)
            .reduce("0", this::addResourceValues);
            
        String totalGpuCapacity = nodes.stream()
            .map(NodeResourceInfo::getGpuCapacity)
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
    }

    /**
     * 모델별 리소스 요약 계산
     */
    private ResourceUsageResponse.ModelResourceSummary calculateModelResourceSummary(
            List<PodResourceInfo> pods, String modelType) {
        
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
            .reduce("0", this::addResourceValues);
            
        String totalMemoryUsage = modelPods.stream()
            .map(PodResourceInfo::getMemoryUsage)
            .reduce("0", this::addResourceValues);
            
        String totalGpuUsage = modelPods.stream()
            .map(PodResourceInfo::getGpuUsage)
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
    }

    /**
     * 상위 리소스 사용 Pod 목록 반환
     */
    public List<PodResourceInfo> getTopResourceUsagePods(String resourceType, int limit) {
        // 현재 실행 중인 모든 Pod 조회 (실제 구현에서는 KubernetesService 호출)
        List<PodResourceInfo> allPods = new ArrayList<>(); // 임시
        
        Comparator<PodResourceInfo> comparator;
        switch (resourceType.toLowerCase()) {
            case "memory":
                comparator = Comparator.comparing(
                    pod -> pod.getMemoryUsagePercent() != null ? pod.getMemoryUsagePercent() : 0.0, 
                    Comparator.reverseOrder());
                break;
            case "gpu":
                comparator = Comparator.comparing(
                    pod -> pod.getGpuUsagePercent() != null ? pod.getGpuUsagePercent() : 0.0, 
                    Comparator.reverseOrder());
                break;
            default: // cpu
                comparator = Comparator.comparing(
                    pod -> pod.getCpuUsagePercent() != null ? pod.getCpuUsagePercent() : 0.0, 
                    Comparator.reverseOrder());
        }

        return allPods.stream()
            .sorted(comparator)
            .limit(limit)
            .collect(Collectors.toList());
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
                
                Map<String, Object> stats = new HashMap<>();
                stats.put("podCount", metrics.stream().map(ResourceMetrics::getResourceName).distinct().count());
                stats.put("avgCpuUsage", metrics.stream()
                    .mapToDouble(m -> m.getCpuUsagePercent() != null ? m.getCpuUsagePercent() : 0.0)
                    .average().orElse(0.0));
                stats.put("avgMemoryUsage", metrics.stream()
                    .mapToDouble(m -> m.getMemoryUsagePercent() != null ? m.getMemoryUsagePercent() : 0.0)
                    .average().orElse(0.0));
                stats.put("avgGpuUsage", metrics.stream()
                    .mapToDouble(m -> m.getGpuUsagePercent() != null ? m.getGpuUsagePercent() : 0.0)
                    .average().orElse(0.0));
                
                namespaceStats.put(namespace, stats);
            }
            
            namespaceUsage.put("namespaces", namespaceStats);
            namespaceUsage.put("timestamp", LocalDateTime.now());
            
        } catch (Exception e) {
            log.error("Error calculating namespace resource usage: {}", e.getMessage());
            namespaceUsage.put("error", e.getMessage());
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
                80.0, 80.0, 80.0, oneHourAgo);
            
            for (ResourceMetrics metric : highUsageMetrics) {
                if (metric.getCpuUsagePercent() != null && metric.getCpuUsagePercent() > 80) {
                    alerts.add(createAlert("HIGH_CPU_USAGE", metric, "CPU usage above 80%"));
                }
                if (metric.getMemoryUsagePercent() != null && metric.getMemoryUsagePercent() > 80) {
                    alerts.add(createAlert("HIGH_MEMORY_USAGE", metric, "Memory usage above 80%"));
                }
                if (metric.getGpuUsagePercent() != null && metric.getGpuUsagePercent() > 80) {
                    alerts.add(createAlert("HIGH_GPU_USAGE", metric, "GPU usage above 80%"));
                }
            }
            
            // 추가 알람 규칙들...
            
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
                status.put("lastSuccessfulCollection", LocalDateTime.now());
            }
            
        } catch (Exception e) {
            log.error("Error checking metrics server status: {}", e.getMessage());
            status.put("available", false);
            status.put("status", "ERROR");
            status.put("error", e.getMessage());
        }
        
        return status;
    }

    // Private helper methods

    private List<String> getTopResourceUsagePodNames(List<PodResourceInfo> pods, String resourceType, int limit) {
        Comparator<PodResourceInfo> comparator;
        switch (resourceType.toLowerCase()) {
            case "memory":
                comparator = Comparator.comparing(
                    pod -> pod.getMemoryUsagePercent() != null ? pod.getMemoryUsagePercent() : 0.0, 
                    Comparator.reverseOrder());
                break;
            case "gpu":
                comparator = Comparator.comparing(
                    pod -> pod.getGpuUsagePercent() != null ? pod.getGpuUsagePercent() : 0.0, 
                    Comparator.reverseOrder());
                break;
            default:
                comparator = Comparator.comparing(
                    pod -> pod.getCpuUsagePercent() != null ? pod.getCpuUsagePercent() : 0.0, 
                    Comparator.reverseOrder());
        }

        return pods.stream()
            .sorted(comparator)
            .limit(limit)
            .map(pod -> pod.getNamespace() + "/" + pod.getName())
            .collect(Collectors.toList());
    }

    private List<String> generateAlerts(List<PodResourceInfo> pods, List<NodeResourceInfo> nodes) {
        List<String> alerts = new ArrayList<>();
        
        // Pod 관련 알람
        for (PodResourceInfo pod : pods) {
            if (pod.getCpuUsagePercent() != null && pod.getCpuUsagePercent() > 90) {
                alerts.add(String.format("CRITICAL: Pod %s/%s CPU usage is %s%%", 
                    pod.getNamespace(), pod.getName(), pod.getCpuUsagePercent().intValue()));
            }
            if (pod.getMemoryUsagePercent() != null && pod.getMemoryUsagePercent() > 90) {
                alerts.add(String.format("CRITICAL: Pod %s/%s Memory usage is %s%%", 
                    pod.getNamespace(), pod.getName(), pod.getMemoryUsagePercent().intValue()));
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
                alerts.add(String.format("WARNING: Node %s CPU usage is %s%%", 
                    node.getName(), node.getCpuUsagePercent().intValue()));
            }
            if (node.getMemoryUsagePercent() != null && node.getMemoryUsagePercent() > 85) {
                alerts.add(String.format("WARNING: Node %s Memory usage is %s%%", 
                    node.getName(), node.getMemoryUsagePercent().intValue()));
            }
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
            .mapToDouble(m -> m.getCpuUsagePercent() != null ? m.getCpuUsagePercent() : 0.0)
            .average().orElse(0.0));
        stats.put("avgMemoryUsage", podMetrics.stream()
            .mapToDouble(m -> m.getMemoryUsagePercent() != null ? m.getMemoryUsagePercent() : 0.0)
            .average().orElse(0.0));
        stats.put("avgGpuUsage", podMetrics.stream()
            .mapToDouble(m -> m.getGpuUsagePercent() != null ? m.getGpuUsagePercent() : 0.0)
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
            .mapToDouble(m -> m.getCpuUsagePercent() != null ? m.getCpuUsagePercent() : 0.0)
            .average().orElse(0.0));
        stats.put("avgMemoryUsage", nodeMetrics.stream()
            .mapToDouble(m -> m.getMemoryUsagePercent() != null ? m.getMemoryUsagePercent() : 0.0)
            .average().orElse(0.0));
        stats.put("avgGpuUsage", nodeMetrics.stream()
            .mapToDouble(m -> m.getGpuUsagePercent() != null ? m.getGpuUsagePercent() : 0.0)
            .average().orElse(0.0));
        
        return stats;
    }

    private Map<String, Object> calculateModelStatistics(List<ResourceMetrics> metrics) {
        Map<String, List<ResourceMetrics>> metricsByModel = metrics.stream()
            .filter(m -> "POD".equals(m.getResourceType()) && m.getModelType() != null)
            .collect(Collectors.groupingBy(ResourceMetrics::getModelType));
        
        Map<String, Object> modelStats = new HashMap<>();
        
        for (Map.Entry<String, List<ResourceMetrics>> entry : metricsByModel.entrySet()) {
            String modelType = entry.getKey();
            List<ResourceMetrics> modelMetrics = entry.getValue();
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("podCount", modelMetrics.stream().map(ResourceMetrics::getResourceName).distinct().count());
            stats.put("avgCpuUsage", modelMetrics.stream()
                .mapToDouble(m -> m.getCpuUsagePercent() != null ? m.getCpuUsagePercent() : 0.0)
                .average().orElse(0.0));
            stats.put("avgMemoryUsage", modelMetrics.stream()
                .mapToDouble(m -> m.getMemoryUsagePercent() != null ? m.getMemoryUsagePercent() : 0.0)
                .average().orElse(0.0));
            stats.put("avgGpuUsage", modelMetrics.stream()
                .mapToDouble(m -> m.getGpuUsagePercent() != null ? m.getGpuUsagePercent() : 0.0)
                .average().orElse(0.0));
            
            modelStats.put(modelType, stats);
        }
        
        return modelStats;
    }

    private Map<String, Object> calculateResourceTrends(List<ResourceMetrics> metrics, int hours) {
        Map<String, Object> trends = new HashMap<>();
        
        // 시간대별 사용률 계산 (단순화된 구현)
        Map<Integer, List<ResourceMetrics>> metricsByHour = metrics.stream()
            .collect(Collectors.groupingBy(m -> m.getTimestamp().getHour()));
        
        Map<Integer, Double> cpuTrendByHour = new HashMap<>();
        Map<Integer, Double> memoryTrendByHour = new HashMap<>();
        Map<Integer, Double> gpuTrendByHour = new HashMap<>();
        
        for (Map.Entry<Integer, List<ResourceMetrics>> entry : metricsByHour.entrySet()) {
            Integer hour = entry.getKey();
            List<ResourceMetrics> hourMetrics = entry.getValue();
            
            double avgCpu = hourMetrics.stream()
                .mapToDouble(m -> m.getCpuUsagePercent() != null ? m.getCpuUsagePercent() : 0.0)
                .average().orElse(0.0);
            double avgMemory = hourMetrics.stream()
                .mapToDouble(m -> m.getMemoryUsagePercent() != null ? m.getMemoryUsagePercent() : 0.0)
                .average().orElse(0.0);
            double avgGpu = hourMetrics.stream()
                .mapToDouble(m -> m.getGpuUsagePercent() != null ? m.getGpuUsagePercent() : 0.0)
                .average().orElse(0.0);
            
            cpuTrendByHour.put(hour, avgCpu);
            memoryTrendByHour.put(hour, avgMemory);
            gpuTrendByHour.put(hour, avgGpu);
        }
        
        trends.put("cpuTrendByHour", cpuTrendByHour);
        trends.put("memoryTrendByHour", memoryTrendByHour);
        trends.put("gpuTrendByHour", gpuTrendByHour);
        
        return trends;
    }

    private Map<String, Double> calculateResourceTrend(List<ResourceMetrics> metrics, String resourceType) {
        Map<String, Double> trend = new HashMap<>();
        
        // 단순한 선형 회귀를 통한 추세 계산
        List<Double> values = new ArrayList<>();
        
        switch (resourceType) {
            case "cpu":
                values = metrics.stream()
                    .mapToDouble(m -> m.getCpuUsagePercent() != null ? m.getCpuUsagePercent() : 0.0)
                    .boxed()
                    .collect(Collectors.toList());
                break;
            case "memory":
                values = metrics.stream()
                    .mapToDouble(m -> m.getMemoryUsagePercent() != null ? m.getMemoryUsagePercent() : 0.0)
                    .boxed()
                    .collect(Collectors.toList());
                break;
            case "gpu":
                values = metrics.stream()
                    .mapToDouble(m -> m.getGpuUsagePercent() != null ? m.getGpuUsagePercent() : 0.0)
                    .boxed()
                    .collect(Collectors.toList());
                break;
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
        
        return trend;
    }

    private String addResourceValues(String value1, String value2) {
        try {
            double val1 = parseResourceValue(value1);
            double val2 = parseResourceValue(value2);
            return String.valueOf(val1 + val2);
        } catch (NumberFormatException e) {
            return "0";
        }
    }

    private double parseResourceValue(String value) {
        if (value == null || value.isEmpty() || "0".equals(value)) {
            return 0.0;
        }
        
        String numericValue = value.replaceAll("[^0-9.]", "");
        try {
            return Double.parseDouble(numericValue);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}