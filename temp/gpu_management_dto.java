// ============================================================================
// GPU Management DTOs - Data Transfer Objects
// ============================================================================

package com.k8s.monitor.dto.gpu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * GPU 장비 정보 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpuDeviceInfo {
    
    // 기본 정보
    private String deviceId;
    private String nodeName;
    private String modelId;
    private String modelName;
    private Integer deviceIndex;
    private String serialNumber;
    private String pciAddress;
    private String gpuUuid;
    private String deviceStatus;
    
    // 현재 상태
    private Double currentTempC;
    private Double currentPowerW;
    private Double currentUtilization;
    private Double memoryUtilization;
    
    // 하드웨어 정보
    private String driverVersion;
    private String firmwareVersion;
    private Boolean migSupport;
    private Integer memoryGb;
    private String architecture;
    
    // 관리 정보
    private LocalDateTime installationDate;
    private LocalDateTime lastMaintenanceDate;
    private LocalDateTime warrantyExpiryDate;
    private Double purchaseCost;
    
    // 할당 정보
    private Boolean allocated;
    private String currentAllocationId;
    private String currentWorkloadType;
    
    // MIG 정보 (MIG 지원 GPU의 경우)
    private List<MigInstanceInfo> migInstances;
    private Integer totalMigInstances;
    private Integer availableMigInstances;
    
    // 성능 메트릭
    private Double avgUtilizationLast24h;
    private Double avgTemperatureLast24h;
    private Double avgPowerDrawLast24h;
    
    // 알람 정보
    private List<String> activeAlerts;
    private String healthStatus; // HEALTHY, WARNING, CRITICAL
}

/**
 * GPU 장비 등록 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpuDeviceRegistrationRequest {
    
    private String nodeName;
    private String modelId;
    private Integer deviceIndex;
    private String serialNumber;
    private String pciAddress;
    private String gpuUuid;
    private String driverVersion;
    private String firmwareVersion;
    private String vbiosVersion;
    private Double purchaseCost;
    private LocalDateTime warrantyExpiryDate;
}

/**
 * GPU 장비 통계 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpuDeviceStatistics {
    
    private Integer totalDevices;
    private Integer activeDevices;
    private Integer migEnabledDevices;
    private Integer maintenanceDevices;
    private Integer failedDevices;
    
    private Map<String, Integer> devicesByNode;
    private Map<String, Integer> devicesByModel;
    private Map<String, Integer> devicesByArchitecture;
    
    private Double totalMemoryCapacityGb;
    private Double availableMemoryCapacityGb;
    private Double memoryUtilizationPercent;
    
    private Double avgUtilization;
    private Double avgTemperature;
    private Double avgPowerConsumption;
    
    private LocalDateTime statisticsTime;
}

/**
 * MIG 인스턴스 정보 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigInstanceInfo {
    
    // 기본 정보
    private String migId;
    private String deviceId;
    private String profileId;
    private String profileName;
    private Integer instanceId;
    private String migUuid;
    
    // 상태 정보
    private Boolean allocated;
    private String instanceStatus;
    private LocalDateTime createdDate;
    private LocalDateTime allocatedDate;
    private LocalDateTime lastUsedDate;
    
    // 리소스 정보
    private Integer memoryGb;
    private Integer computeSlices;
    private Integer memorySlices;
    private Double performanceRatio;
    
    // 할당 정보
    private String currentAllocationId;
    private String currentNamespace;
    private String currentPodName;
    
    // 사용량 정보
    private Double currentUtilization;
    private Double memoryUtilization;
    private Double avgUtilizationLast24h;
}

/**
 * MIG 프로필 정보 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigProfileInfo {
    
    private String profileId;
    private String modelId;
    private String profileName;
    private Integer computeSlices;
    private Integer memorySlices;
    private Integer memoryGb;
    private Integer maxInstancesPerGpu;
    private Double performanceRatio;
    private String useCase;
    private String description;
    
    // 사용량 통계
    private Integer totalInstances;
    private Integer allocatedInstances;
    private Integer availableInstances;
    private Double utilizationPercent;
}

/**
 * GPU 할당 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpuAllocationRequest {
    
    // Pod 정보
    private String namespace;
    private String podName;
    private String containerName;
    
    // 워크로드 정보
    private String workloadType; // Training, Inference, Development, Gaming
    private String priorityClass;
    
    // 리소스 요구사항
    private Boolean useMig;
    private Integer requiredMemoryGb;
    private String preferredModelId;
    private String preferredArchitecture;
    
    // 스케줄링 정보
    private LocalDateTime plannedReleaseTime;
    private Integer maxDurationHours;
    
    // 사용자 정보
    private String userId;
    private String teamId;
    private String projectId;
    
    // 비용 관리
    private Double maxCostPerHour;
    private Double maxTotalCost;
    
    // 선호도 설정
    private Boolean preferHighMemory;
    private Boolean preferNewGeneration;
    private Boolean allowSharedGpu;
}

/**
 * GPU 할당 정보 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpuAllocationInfo {
    
    // 할당 기본 정보
    private String allocationId;
    private String namespace;
    private String podName;
    private String containerName;
    private String workloadType;
    
    // 리소스 정보
    private String resourceType; // FULL_GPU, MIG_INSTANCE, SHARED_GPU
    private String allocatedResource; // device_id or mig_id
    private Integer requestedMemoryGb;
    private Integer allocatedMemoryGb;
    private String priorityClass;
    
    // 시간 정보
    private LocalDateTime allocationTime;
    private LocalDateTime plannedReleaseTime;
    private LocalDateTime releaseTime;
    private String status;
    private Long usageDurationHours;
    
    // 비용 정보
    private Double costPerHour;
    private Double totalCost;
    private Double estimatedMonthlyCost;
    
    // 사용자 정보
    private String userId;
    private String teamId;
    private String projectId;
    
    // 추가 정보
    private GpuDeviceInfo deviceInfo;
    private MigInstanceInfo migInstanceInfo;
    private Map<String, String> labels;
    private Map<String, String> annotations;
}

/**
 * GPU 사용량 메트릭 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpuUsageMetricInfo {
    
    private String deviceId;
    private String migId;
    private String allocationId;
    private LocalDateTime timestamp;
    
    // GPU 사용률
    private Double gpuUtilizationPct;
    private Double memoryUtilizationPct;
    private Long memoryUsedMb;
    private Long memoryTotalMb;
    
    // 하드웨어 상태
    private Double temperatureC;
    private Double powerDrawW;
    private Double fanSpeedPct;
    
    // 클럭 정보
    private Integer clockGraphicsMhz;
    private Integer clockMemoryMhz;
    
    // 네트워크 정보
    private Double pcieTxMbps;
    private Double pcieRxMbps;
    
    // 프로세스 정보
    private Integer processesCount;
    private String collectionSource;
    
    // 계산된 필드
    private String utilizationLevel; // LOW, MEDIUM, HIGH, CRITICAL
    private String temperatureLevel; // NORMAL, WARNING, CRITICAL
    private Boolean isOverheating;
    private Boolean isHighUtilization;
}

/**
 * GPU 클러스터 개요 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpuClusterOverview {
    
    // 기본 통계
    private Integer totalNodes;
    private Integer totalGpuDevices;
    private Integer totalMigInstances;
    private Integer activeAllocations;
    
    // 모델별 분포
    private Map<String, Integer> devicesByModel;
    private Map<String, Integer> devicesByArchitecture;
    private Map<String, Integer> devicesByGeneration;
    
    // 사용률 정보
    private Double overallGpuUtilization;
    private Double overallMemoryUtilization;
    private Double overallTemperature;
    private Double overallPowerConsumption;
    
    // 할당 정보
    private Map<String, Integer> allocationsByWorkloadType;
    private Map<String, Integer> allocationsByNamespace;
    private Map<String, Integer> allocationsByTeam;
    
    // 비용 정보
    private Double totalHourlyCost;
    private Double totalMonthlyCost;
    private Double costByWorkloadType;
    
    // 용량 정보
    private Integer totalMemoryCapacityGb;
    private Integer availableMemoryCapacityGb;
    private Double memoryUtilizationPercent;
    
    // 알람 정보
    private Integer totalAlerts;
    private Integer criticalAlerts;
    private Integer warningAlerts;
    private List<String> topAlerts;
    
    // 성능 동향
    private Map<String, Double> utilizationTrend24h;
    private Map<String, Double> temperatureTrend24h;
    
    private LocalDateTime lastUpdated;
}

/**
 * GPU 비용 분석 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpuCostAnalysis {
    
    // 기간별 비용
    private Double dailyCost;
    private Double weeklyCost;
    private Double monthlyCost;
    private Double quarterlyCost;
    
    // 분류별 비용
    private Map<String, Double> costByNamespace;
    private Map<String, Double> costByTeam;
    private Map<String, Double> costByProject;
    private Map<String, Double> costByWorkloadType;
    private Map<String, Double> costByGpuModel;
    
    // 사용률별 분석
    private Double costFromHighUtilization;
    private Double costFromMediumUtilization;
    private Double costFromLowUtilization;
    private Double costFromIdleTime;
    
    // 최적화 제안
    private List<CostOptimizationSuggestion> optimizationSuggestions;
    private Double potentialMonthlySavings;
    
    // 예측
    private Double predictedMonthlyCost;
    private Double budgetVariance;
    
    private LocalDateTime analysisDate;
    private String analysisTimeRange;
}

/**
 * 비용 최적화 제안 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostOptimizationSuggestion {
    
    private String suggestionType; // RIGHTSIZING, SCHEDULING, SHARING, TERMINATION
    private String title;
    private String description;
    private String targetResource;
    private Double currentMonthlyCost;
    private Double optimizedMonthlyCost;
    private Double potentialSavings;
    private String priority; // HIGH, MEDIUM, LOW
    private String implementation;
    private String impact;
}

/**
 * GPU 성능 벤치마크 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpuBenchmarkInfo {
    
    private String benchmarkId;
    private String modelId;
    private String modelName;
    private String benchmarkType; // FP32, FP16, INT8, Training, Inference, Gaming
    private String benchmarkName; // ResNet-50, BERT, 3DMark, etc.
    
    private Double score;
    private String scoreUnit; // FPS, TOPS, Images/sec, Tokens/sec
    private Integer batchSize;
    private String precision; // FP32, FP16, INT8, Mixed
    private String framework; // TensorFlow, PyTorch, CUDA
    
    private Double testDurationMinutes;
    private LocalDateTime testDate;
    private String testEnvironment;
    private String hardwareConfig;
    private String softwareConfig;
    
    private Double relativePerformance; // Compared to baseline
    private String performanceCategory; // EXCELLENT, GOOD, AVERAGE, POOR
    private String notes;
}

/**
 * GPU 알람 정보 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpuAlertInfo {
    
    private String alertId;
    private String alertType; // TEMPERATURE, UTILIZATION, MEMORY, POWER, FAILURE
    private String severity; // CRITICAL, WARNING, INFO
    private String status; // ACTIVE, ACKNOWLEDGED, RESOLVED
    
    private String targetType; // DEVICE, MIG_INSTANCE, NODE
    private String targetId;
    private String targetName;
    
    private String message;
    private String description;
    private Double threshold;
    private Double currentValue;
    
    private LocalDateTime triggeredAt;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime resolvedAt;
    private String acknowledgedBy;
    private String resolvedBy;
    
    private String resolution;
    private String impact;
    private List<String> recommendedActions;
}

/**
 * GPU 작업 프로필 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpuWorkloadProfile {
    
    private String profileId;
    private String workloadName;
    private String workloadType;
    
    // 리소스 요구사항
    private Integer minMemoryGb;
    private Integer preferredMemoryGb;
    private String minComputeCapability;
    private List<String> preferredArchitectures;
    private Boolean requiresMig;
    private Integer maxSharingRatio;
    
    // 성능 요구사항
    private Map<String, Object> performanceRequirements;
    private Map<String, Object> resourceConstraints;
    private String costSensitivity; // LOW, MEDIUM, HIGH
    private Map<String, Object> slaRequirements;
    
    // 사용 통계
    private Integer totalAllocations;
    private Double avgDurationHours;
    private Double avgCost;
    private Double avgUtilization;
    
    private String description;
    private String createdBy;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
}

/**
 * GPU 예측 분석 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpuForecastAnalysis {
    
    // 사용량 예측
    private Map<String, Double> utilizationForecast24h;
    private Map<String, Double> utilizationForecast7d;
    private Map<String, Double> utilizationForecast30d;
    
    // 비용 예측
    private Double costForecast24h;
    private Double costForecast7d;
    private Double costForecast30d;
    
    // 용량 예측
    private LocalDateTime capacityExhaustionEta;
    private Integer additionalGpusNeeded;
    private String recommendedGpuModels;
    
    // 트렌드 분석
    private String utilizationTrend; // INCREASING, DECREASING, STABLE
    private String costTrend;
    private String demandTrend;
    
    // 계절성 분석
    private Map<String, Double> seasonalPatterns;
    private List<String> peakUsagePeriods;
    
    // 추천사항
    private List<String> scalingRecommendations;
    private List<String> optimizationRecommendations;
    
    private LocalDateTime forecastDate;
    private String forecastMethod;
    private Double confidence;
}