package com.k8s.monitor.service;

import com.k8s.monitor.dto.NodeResourceInfo;
import com.k8s.monitor.dto.PodResourceInfo;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.custom.Quantity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Kubernetes API와 연동하여 리소스 정보를 조회하는 서비스
 * Pod과 Node의 기본 정보, 리소스 할당량 등을 수집
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KubernetesService {
    
    private final CoreV1Api coreV1Api;
    private final ResourceMetricsService metricsService;

    /**
     * 모델 서빙 Pod 목록 조회 (vLLM, SGLang)
     * @param namespace 특정 네임스페이스 (null이면 전체)
     * @return Pod 리소스 정보 목록
     */
    public List<PodResourceInfo> getModelServingPods(String namespace) {
        try {
            V1PodList podList;
            String labelSelector = "app in (vllm,sglang)";
            
            if (namespace != null && !namespace.isEmpty()) {
                podList = coreV1Api.listNamespacedPod(
                    namespace,     // namespace
                    null,          // pretty
                    null,          // allowWatchBookmarks
                    null,          // _continue
                    null,          // fieldSelector
                    labelSelector, // labelSelector
                    null,          // limit
                    null,          // resourceVersion
                    null,          // resourceVersionMatch
                    null,          // sendInitialEvents
                    null,          // timeoutSeconds
                    null           // watch
                );
            } else {
                podList = coreV1Api.listPodForAllNamespaces(
                    null,          // allowWatchBookmarks
                    null,          // _continue
                    null,          // fieldSelector
                    labelSelector, // labelSelector
                    null,          // limit
                    null,          // pretty
                    null,          // resourceVersion
                    null,          // resourceVersionMatch
                    null,          // sendInitialEvents
                    null,          // timeoutSeconds
                    null           // watch
                );
            }

            return podList.getItems().stream()
                .map(this::mapToPodResourceInfo)
                .collect(Collectors.toList());
                
        } catch (ApiException e) {
            log.error("Error fetching model serving pods: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 전체 Pod 목록 조회 (네임스페이스별)
     */
    public List<PodResourceInfo> getAllPods(String namespace) {
        try {
            V1PodList podList;
            
            if (namespace != null && !namespace.isEmpty()) {
                podList = coreV1Api.listNamespacedPod(
                    namespace, // namespace
                    null,      // pretty
                    null,      // allowWatchBookmarks
                    null,      // _continue
                    null,      // fieldSelector
                    null,      // labelSelector
                    null,      // limit
                    null,      // resourceVersion
                    null,      // resourceVersionMatch
                    null,      // sendInitialEvents
                    null,      // timeoutSeconds
                    null       // watch
                );
            } else {
                podList = coreV1Api.listPodForAllNamespaces(
                    null,      // allowWatchBookmarks
                    null,      // _continue
                    null,      // fieldSelector
                    null,      // labelSelector
                    null,      // limit
                    null,      // pretty
                    null,      // resourceVersion
                    null,      // resourceVersionMatch
                    null,      // sendInitialEvents
                    null,      // timeoutSeconds
                    null       // watch
                );
            }

            return podList.getItems().stream()
                .map(this::mapToPodResourceInfo)
                .collect(Collectors.toList());
                
        } catch (ApiException e) {
            log.error("Error fetching all pods: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 노드 리소스 정보 조회
     */
    public List<NodeResourceInfo> getNodeResourceInfo() {
        try {
            V1NodeList nodeList = coreV1Api.listNode(
                null,      // pretty
                null,      // allowWatchBookmarks
                null,      // _continue
                null,      // fieldSelector
                null,      // labelSelector
                null,      // limit
                null,      // resourceVersion
                null,      // resourceVersionMatch
                null,      // sendInitialEvents
                null,      // timeoutSeconds
                null       // watch
            );

            return nodeList.getItems().stream()
                .map(this::mapToNodeResourceInfo)
                .collect(Collectors.toList());
                
        } catch (ApiException e) {
            log.error("Error fetching nodes: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 특정 Pod 상세 정보 조회
     */
    public PodResourceInfo getPodDetails(String namespace, String podName) {
        try {
            V1Pod pod = coreV1Api.readNamespacedPod(podName, namespace, null);
            return mapToPodResourceInfo(pod);
        } catch (ApiException e) {
            log.error("Error fetching pod details for {}/{}: {}", namespace, podName, e.getMessage());
            return null;
        }
    }

    /**
     * 특정 노드 상세 정보 조회
     */
    public NodeResourceInfo getNodeDetails(String nodeName) {
        try {
            V1Node node = coreV1Api.readNode(nodeName, null);
            return mapToNodeResourceInfo(node);
        } catch (ApiException e) {
            log.error("Error fetching node details for {}: {}", nodeName, e.getMessage());
            return null;
        }
    }

    /**
     * Pod을 PodResourceInfo DTO로 변환
     */
    private PodResourceInfo mapToPodResourceInfo(V1Pod pod) {
        V1ObjectMeta metadata = pod.getMetadata();
        V1PodSpec spec = pod.getSpec();
        V1PodStatus status = pod.getStatus();

        // 리소스 요구사항 추출
        ResourceRequirements resources = extractResourceRequirements(spec);
        
        // 현재 사용량 조회
        Map<String, String> currentUsage = metricsService.getPodMetrics(
            metadata.getNamespace(), metadata.getName());

        // 라벨에서 모델 정보 추출
        Map<String, String> labels = metadata.getLabels();
        String modelType = extractModelType(labels);
        String modelName = extractModelName(labels);
        String modelVersion = extractModelVersion(labels);

        // 사용률 계산
        Double cpuUsagePercent = calculateUsagePercent(
            currentUsage.get("cpu"), resources.cpuLimit);
        Double memoryUsagePercent = calculateUsagePercent(
            currentUsage.get("memory"), resources.memoryLimit);
        Double gpuUsagePercent = calculateUsagePercent(
            currentUsage.get("gpu"), resources.gpuLimit);

        return PodResourceInfo.builder()
            .name(metadata.getName())
            .namespace(metadata.getNamespace())
            .nodeName(spec != null ? spec.getNodeName() : "unknown")
            .phase(status != null ? status.getPhase() : "unknown")
            .creationTime(metadata.getCreationTimestamp() != null ? 
                LocalDateTime.ofInstant(metadata.getCreationTimestamp().toInstant(), ZoneId.systemDefault()) : null)
            .cpuRequest(resources.cpuRequest)
            .memoryRequest(resources.memoryRequest)
            .gpuRequest(resources.gpuRequest)
            .cpuLimit(resources.cpuLimit)
            .memoryLimit(resources.memoryLimit)
            .gpuLimit(resources.gpuLimit)
            .cpuUsage(currentUsage.getOrDefault("cpu", "0"))
            .memoryUsage(currentUsage.getOrDefault("memory", "0"))
            .gpuUsage(currentUsage.getOrDefault("gpu", "0"))
            .cpuUsagePercent(cpuUsagePercent)
            .memoryUsagePercent(memoryUsagePercent)
            .gpuUsagePercent(gpuUsagePercent)
            .labels(labels)
            .annotations(metadata.getAnnotations())
            .modelType(modelType)
            .modelName(modelName)
            .modelVersion(modelVersion)
            .replicas(1)
            .containerStatus(getContainerStatus(status))
            .readyStatus(getPodReadyStatus(status))
            .restartCount(getRestartCount(status))
            .build();
    }

    /**
     * Node를 NodeResourceInfo DTO로 변환
     */
    private NodeResourceInfo mapToNodeResourceInfo(V1Node node) {
        V1ObjectMeta metadata = node.getMetadata();
        V1NodeStatus status = node.getStatus();
        
        Map<String, Quantity> capacity = status.getCapacity();
        Map<String, Quantity> allocatable = status.getAllocatable();
        
        // 현재 사용량 조회
        Map<String, String> currentUsage = metricsService.getNodeMetrics(metadata.getName());
        
        // 용량 정보 (Quantity를 String으로 변환하는 헬퍼 메서드 사용)
        String cpuCapacity = convertQuantityToString(capacity, "cpu");
        String memoryCapacity = convertQuantityToString(capacity, "memory");
        String gpuCapacity = convertQuantityToString(capacity, "nvidia.com/gpu");
        String storageCapacity = convertQuantityToString(capacity, "ephemeral-storage");
        
        String cpuAllocatable = convertQuantityToString(allocatable, "cpu");
        String memoryAllocatable = convertQuantityToString(allocatable, "memory");
        String gpuAllocatable = convertQuantityToString(allocatable, "nvidia.com/gpu");
        String storageAllocatable = convertQuantityToString(allocatable, "ephemeral-storage");
        
        // 사용량 정보
        String cpuUsage = currentUsage.getOrDefault("cpu", "0");
        String memoryUsage = currentUsage.getOrDefault("memory", "0");
        String gpuUsage = currentUsage.getOrDefault("gpu", "0");
        String storageUsage = currentUsage.getOrDefault("storage", "0");

        // Pod 개수 조회
        PodCounts podCounts = getPodCountsForNode(metadata.getName());

        return NodeResourceInfo.builder()
            .name(metadata.getName())
            .role(getNodeRole(metadata.getLabels()))
            .status(getNodeStatus(status))
            .lastUpdateTime(LocalDateTime.now())
            .cpuCapacity(cpuCapacity)
            .memoryCapacity(memoryCapacity)
            .gpuCapacity(gpuCapacity)
            .storageCapacity(storageCapacity)
            .cpuAllocatable(cpuAllocatable)
            .memoryAllocatable(memoryAllocatable)
            .gpuAllocatable(gpuAllocatable)
            .storageAllocatable(storageAllocatable)
            .cpuUsage(cpuUsage)
            .memoryUsage(memoryUsage)
            .gpuUsage(gpuUsage)
            .storageUsage(storageUsage)
            .cpuUsagePercent(calculateUsagePercent(cpuUsage, cpuAllocatable))
            .memoryUsagePercent(calculateUsagePercent(memoryUsage, memoryAllocatable))
            .gpuUsagePercent(calculateUsagePercent(gpuUsage, gpuAllocatable))
            .storageUsagePercent(calculateUsagePercent(storageUsage, storageAllocatable))
            .labels(metadata.getLabels())
            .annotations(metadata.getAnnotations())
            .totalPodCount(podCounts.total)
            .runningPodCount(podCounts.running)
            .vllmPodCount(podCounts.vllm)
            .sglangPodCount(podCounts.sglang)
            .kubeletVersion(status.getNodeInfo() != null ? status.getNodeInfo().getKubeletVersion() : "unknown")
            .containerRuntimeVersion(status.getNodeInfo() != null ? status.getNodeInfo().getContainerRuntimeVersion() : "unknown")
            .operatingSystem(status.getNodeInfo() != null ? status.getNodeInfo().getOperatingSystem() : "unknown")
            .architecture(status.getNodeInfo() != null ? status.getNodeInfo().getArchitecture() : "unknown")
            .build();
    }

    // Helper classes and methods
    
    private static class ResourceRequirements {
        String cpuRequest = "0";
        String memoryRequest = "0";
        String gpuRequest = "0";
        String cpuLimit = "0";
        String memoryLimit = "0";
        String gpuLimit = "0";
    }

    private static class PodCounts {
        int total = 0;
        int running = 0;
        int vllm = 0;
        int sglang = 0;
    }

    private ResourceRequirements extractResourceRequirements(V1PodSpec spec) {
        ResourceRequirements requirements = new ResourceRequirements();
        
        if (spec != null && spec.getContainers() != null) {
            for (V1Container container : spec.getContainers()) {
                V1ResourceRequirements resources = container.getResources();
                if (resources != null) {
                    if (resources.getRequests() != null) {
                        requirements.cpuRequest = addResourceValues(requirements.cpuRequest, 
                            convertQuantityToString(resources.getRequests(), "cpu"));
                        requirements.memoryRequest = addResourceValues(requirements.memoryRequest, 
                            convertQuantityToString(resources.getRequests(), "memory"));
                        requirements.gpuRequest = addResourceValues(requirements.gpuRequest, 
                            convertQuantityToString(resources.getRequests(), "nvidia.com/gpu"));
                    }
                    if (resources.getLimits() != null) {
                        requirements.cpuLimit = addResourceValues(requirements.cpuLimit, 
                            convertQuantityToString(resources.getLimits(), "cpu"));
                        requirements.memoryLimit = addResourceValues(requirements.memoryLimit, 
                            convertQuantityToString(resources.getLimits(), "memory"));
                        requirements.gpuLimit = addResourceValues(requirements.gpuLimit, 
                            convertQuantityToString(resources.getLimits(), "nvidia.com/gpu"));
                    }
                }
            }
        }
        
        return requirements;
    }

    private PodCounts getPodCountsForNode(String nodeName) {
        PodCounts counts = new PodCounts();
        
        try {
            V1PodList podList = coreV1Api.listPodForAllNamespaces(
                null,      // allowWatchBookmarks
                null,      // _continue
                "spec.nodeName=" + nodeName, // fieldSelector
                null,      // labelSelector
                null,      // limit
                null,      // pretty
                null,      // resourceVersion
                null,      // resourceVersionMatch
                null,      // sendInitialEvents
                null,      // timeoutSeconds
                null       // watch
            );
            
            counts.total = podList.getItems().size();
            
            for (V1Pod pod : podList.getItems()) {
                if ("Running".equals(pod.getStatus().getPhase())) {
                    counts.running++;
                }
                
                Map<String, String> labels = pod.getMetadata().getLabels();
                if (labels != null) {
                    String app = labels.get("app");
                    if ("vllm".equals(app)) {
                        counts.vllm++;
                    } else if ("sglang".equals(app)) {
                        counts.sglang++;
                    }
                }
            }
        } catch (ApiException e) {
            log.error("Error counting pods for node {}: {}", nodeName, e.getMessage());
        }
        
        return counts;
    }

    // Utility methods
    
    /**
     * Quantity 객체에서 String 값 추출하는 수정된 메서드
     */
    private String convertQuantityToString(Map<String, Quantity> resources, String key) {
        if (resources == null) {
            return "0";
        }
        Quantity quantity = resources.get(key);
        return quantity != null ? quantity.toSuffixedString() : "0";
    }

    private String extractModelType(Map<String, String> labels) {
        if (labels != null) {
            String app = labels.get("app");
            if ("vllm".equals(app) || "sglang".equals(app)) {
                return app;
            }
        }
        return "unknown";
    }

    private String extractModelName(Map<String, String> labels) {
        return labels != null ? labels.getOrDefault("model", "unknown") : "unknown";
    }

    private String extractModelVersion(Map<String, String> labels) {
        return labels != null ? labels.getOrDefault("version", "unknown") : "unknown";
    }

    private String getNodeRole(Map<String, String> labels) {
        if (labels != null) {
            if (labels.containsKey("node-role.kubernetes.io/master") || 
                labels.containsKey("node-role.kubernetes.io/control-plane")) {
                return "master";
            }
        }
        return "worker";
    }

    private String getNodeStatus(V1NodeStatus status) {
        if (status != null && status.getConditions() != null) {
            return status.getConditions().stream()
                .filter(condition -> "Ready".equals(condition.getType()))
                .map(condition -> "True".equals(condition.getStatus()) ? "Ready" : "NotReady")
                .findFirst()
                .orElse("Unknown");
        }
        return "Unknown";
    }

    private String getContainerStatus(V1PodStatus status) {
        if (status != null && status.getContainerStatuses() != null && !status.getContainerStatuses().isEmpty()) {
            V1ContainerStatus containerStatus = status.getContainerStatuses().get(0);
            if (containerStatus.getState() != null) {
                if (containerStatus.getState().getRunning() != null) return "Running";
                if (containerStatus.getState().getWaiting() != null) return "Waiting";
                if (containerStatus.getState().getTerminated() != null) return "Terminated";
            }
        }
        return "Unknown";
    }

    private String getPodReadyStatus(V1PodStatus status) {
        if (status != null && status.getConditions() != null) {
            return status.getConditions().stream()
                .filter(condition -> "Ready".equals(condition.getType()))
                .map(condition -> condition.getStatus())
                .findFirst()
                .orElse("Unknown");
        }
        return "Unknown";
    }

    private Integer getRestartCount(V1PodStatus status) {
        if (status != null && status.getContainerStatuses() != null && !status.getContainerStatuses().isEmpty()) {
            return status.getContainerStatuses().stream()
                .mapToInt(V1ContainerStatus::getRestartCount)
                .sum();
        }
        return 0;
    }

    private Double calculateUsagePercent(String usage, String limit) {
        try {
            if (usage == null || limit == null || "0".equals(limit)) {
                return 0.0;
            }
            
            double usageValue = parseResourceValue(usage);
            double limitValue = parseResourceValue(limit);
            
            return limitValue > 0 ? Math.min((usageValue / limitValue) * 100, 100.0) : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private double parseResourceValue(String value) {
        if (value == null || value.isEmpty() || "0".equals(value)) {
            return 0.0;
        }
        
        // 단위 제거 후 숫자만 추출
        String numericValue = value.replaceAll("[^0-9.]", "");
        try {
            return Double.parseDouble(numericValue);
        } catch (NumberFormatException e) {
            return 0.0;
        }
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
}