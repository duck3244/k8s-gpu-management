package com.k8s.monitor.dto.gpu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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