package com.k8s.monitor.dto.gpu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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