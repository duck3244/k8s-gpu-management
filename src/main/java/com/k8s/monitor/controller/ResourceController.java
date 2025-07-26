package com.k8s.monitor.controller;

import com.k8s.monitor.dto.NodeResourceInfo;
import com.k8s.monitor.dto.PodResourceInfo;
import com.k8s.monitor.dto.ResourceUsageResponse;
import com.k8s.monitor.service.KubernetesService;
import com.k8s.monitor.service.ResourceAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Kubernetes 리소스 모니터링 REST API 컨트롤러
 * Pod, Node, 클러스터 전체의 리소스 사용량 조회 API 제공
 */
@RestController
@RequestMapping("/api/v1/resources")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class ResourceController {
    
    private final KubernetesService kubernetesService;
    private final ResourceAnalysisService analysisService;

    /**
     * 모델 서빙 Pod 목록 조회 (vLLM, SGLang)
     * @param namespace 네임스페이스 필터 (선택사항)
     * @return 모델 서빙 Pod 리스트
     */
    @GetMapping("/pods")
    public ResponseEntity<List<PodResourceInfo>> getModelServingPods(
            @RequestParam(required = false) String namespace) {
        log.info("Fetching model serving pods for namespace: {}", namespace);
        
        try {
            List<PodResourceInfo> pods = kubernetesService.getModelServingPods(namespace);
            log.debug("Found {} model serving pods", pods.size());
            return ResponseEntity.ok(pods);
        } catch (Exception e) {
            log.error("Error fetching model serving pods: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 전체 Pod 목록 조회
     * @param namespace 네임스페이스 필터 (선택사항)
     * @return 전체 Pod 리스트
     */
    @GetMapping("/pods/all")
    public ResponseEntity<List<PodResourceInfo>> getAllPods(
            @RequestParam(required = false) String namespace) {
        log.info("Fetching all pods for namespace: {}", namespace);
        
        try {
            List<PodResourceInfo> pods = kubernetesService.getAllPods(namespace);
            return ResponseEntity.ok(pods);
        } catch (Exception e) {
            log.error("Error fetching all pods: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 노드 리소스 정보 조회
     * @return 노드 리스트
     */
    @GetMapping("/nodes")
    public ResponseEntity<List<NodeResourceInfo>> getNodes() {
        log.info("Fetching node resource information");
        
        try {
            List<NodeResourceInfo> nodes = kubernetesService.getNodeResourceInfo();
            log.debug("Found {} nodes", nodes.size());
            return ResponseEntity.ok(nodes);
        } catch (Exception e) {
            log.error("Error fetching nodes: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 통합 리소스 사용량 조회 (Pod + Node + 클러스터 요약)
     * @param namespace 네임스페이스 필터 (선택사항)
     * @return 통합 리소스 사용량 정보
     */
    @GetMapping("/usage")
    public ResponseEntity<ResourceUsageResponse> getResourceUsage(
            @RequestParam(required = false) String namespace) {
        log.info("Fetching complete resource usage for namespace: {}", namespace);
        
        try {
            List<PodResourceInfo> pods = kubernetesService.getModelServingPods(namespace);
            List<NodeResourceInfo> nodes = kubernetesService.getNodeResourceInfo();
            
            // 클러스터 요약 정보 계산
            ResourceUsageResponse.ClusterSummary summary = analysisService.calculateClusterSummary(pods, nodes);
            
            ResourceUsageResponse response = ResourceUsageResponse.builder()
                .pods(pods)
                .nodes(nodes)
                .clusterSummary(summary)
                .timestamp(LocalDateTime.now())
                .build();
                
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching resource usage: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 특정 Pod 상세 정보 조회
     * @param namespace 네임스페이스
     * @param podName Pod 이름
     * @return Pod 상세 정보
     */
    @GetMapping("/pods/{namespace}/{podName}")
    public ResponseEntity<PodResourceInfo> getPodDetails(
            @PathVariable String namespace,
            @PathVariable String podName) {
        log.info("Fetching details for pod: {}/{}", namespace, podName);
        
        try {
            PodResourceInfo pod = kubernetesService.getPodDetails(namespace, podName);
            
            if (pod != null) {
                return ResponseEntity.ok(pod);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error fetching pod details for {}/{}: {}", namespace, podName, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 특정 노드 상세 정보 조회
     * @param nodeName 노드 이름
     * @return 노드 상세 정보
     */
    @GetMapping("/nodes/{nodeName}")
    public ResponseEntity<NodeResourceInfo> getNodeDetails(@PathVariable String nodeName) {
        log.info("Fetching details for node: {}", nodeName);
        
        try {
            NodeResourceInfo node = kubernetesService.getNodeDetails(nodeName);
            
            if (node != null) {
                return ResponseEntity.ok(node);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error fetching node details for {}: {}", nodeName, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 모델 타입별 리소스 사용량 조회
     * @param modelType 모델 타입 (vllm, sglang)
     * @return 모델별 리소스 정보
     */
    @GetMapping("/models/{modelType}")
    public ResponseEntity<List<PodResourceInfo>> getPodsByModelType(@PathVariable String modelType) {
        log.info("Fetching pods for model type: {}", modelType);
        
        try {
            List<PodResourceInfo> pods = kubernetesService.getModelServingPods(null);
            List<PodResourceInfo> filteredPods = pods.stream()
                .filter(pod -> modelType.equalsIgnoreCase(pod.getModelType()))
                .toList();
                
            return ResponseEntity.ok(filteredPods);
        } catch (Exception e) {
            log.error("Error fetching pods for model type {}: {}", modelType, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 리소스 사용률 상위 Pod 조회
     * @param resourceType 리소스 타입 (cpu, memory, gpu)
     * @param limit 조회할 개수 (기본값: 10)
     * @return 상위 리소스 사용 Pod 리스트
     */
    @GetMapping("/pods/top")
    public ResponseEntity<List<PodResourceInfo>> getTopResourceUsagePods(
            @RequestParam(defaultValue = "cpu") String resourceType,
            @RequestParam(defaultValue = "10") int limit) {
        log.info("Fetching top {} resource usage pods, limit: {}", resourceType, limit);
        
        try {
            List<PodResourceInfo> topPods = analysisService.getTopResourceUsagePods(resourceType, limit);
            return ResponseEntity.ok(topPods);
        } catch (Exception e) {
            log.error("Error fetching top resource usage pods: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 클러스터 헬스 상태 조회
     * @return 클러스터 헬스 정보
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getClusterHealth() {
        log.info("Fetching cluster health status");
        
        try {
            Map<String, Object> health = analysisService.getClusterHealthStatus();
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            log.error("Error fetching cluster health: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 리소스 사용량 통계 조회
     * @param hours 조회 시간 범위 (기본값: 24시간)
     * @return 리소스 사용량 통계
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getResourceStatistics(
            @RequestParam(defaultValue = "24") int hours) {
        log.info("Fetching resource statistics for last {} hours", hours);
        
        try {
            Map<String, Object> statistics = analysisService.getResourceStatistics(hours);
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            log.error("Error fetching resource statistics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 네임스페이스별 리소스 사용량 조회
     * @return 네임스페이스별 리소스 정보
     */
    @GetMapping("/namespaces")
    public ResponseEntity<Map<String, Object>> getNamespaceResourceUsage() {
        log.info("Fetching namespace resource usage");
        
        try {
            Map<String, Object> namespaceUsage = analysisService.getNamespaceResourceUsage();
            return ResponseEntity.ok(namespaceUsage);
        } catch (Exception e) {
            log.error("Error fetching namespace resource usage: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 리소스 알람 조회
     * @return 현재 활성 알람 리스트
     */
    @GetMapping("/alerts")
    public ResponseEntity<List<Map<String, Object>>> getResourceAlerts() {
        log.info("Fetching resource alerts");
        
        try {
            List<Map<String, Object>> alerts = analysisService.getResourceAlerts();
            return ResponseEntity.ok(alerts);
        } catch (Exception e) {
            log.error("Error fetching resource alerts: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 리소스 예측 정보 조회
     * @param hours 예측 시간 범위 (기본값: 24시간)
     * @return 리소스 사용량 예측 정보
     */
    @GetMapping("/forecast")
    public ResponseEntity<Map<String, Object>> getResourceForecast(
            @RequestParam(defaultValue = "24") int hours) {
        log.info("Fetching resource forecast for next {} hours", hours);
        
        try {
            Map<String, Object> forecast = analysisService.getResourceForecast(hours);
            return ResponseEntity.ok(forecast);
        } catch (Exception e) {
            log.error("Error fetching resource forecast: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 메트릭 서버 상태 확인
     * @return 메트릭 서버 상태 정보
     */
    @GetMapping("/metrics/status")
    public ResponseEntity<Map<String, Object>> getMetricsServerStatus() {
        log.info("Checking metrics server status");
        
        try {
            Map<String, Object> status = analysisService.getMetricsServerStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Error checking metrics server status: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}