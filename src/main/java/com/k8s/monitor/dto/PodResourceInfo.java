package com.k8s.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Pod 리소스 정보를 담는 DTO
 * vLLM, SGLang 모델 서빙 Pod의 리소스 할당량 및 사용량 정보
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PodResourceInfo {
    
    // 기본 정보
    private String name;
    private String namespace;
    private String nodeName;
    private String phase;
    private LocalDateTime creationTime;
    
    // 리소스 요청량 (Requests)
    private String cpuRequest;
    private String memoryRequest;
    private String gpuRequest;
    
    // 리소스 제한량 (Limits)
    private String cpuLimit;
    private String memoryLimit;
    private String gpuLimit;
    
    // 현재 사용량 (메트릭 서버에서 수집)
    private String cpuUsage;
    private String memoryUsage;
    private String gpuUsage;
    
    // 사용률 (%)
    private Double cpuUsagePercent;
    private Double memoryUsagePercent;
    private Double gpuUsagePercent;
    
    // 메타데이터
    private Map<String, String> labels;
    private Map<String, String> annotations;
    
    // 모델 서빙 관련 정보
    private String modelType; // vllm, sglang
    private String modelName;
    private String modelVersion;
    private Integer replicas;
    
    // 상태 정보
    private String containerStatus;
    private String readyStatus;
    private Integer restartCount;
}