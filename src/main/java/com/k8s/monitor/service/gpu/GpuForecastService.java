package com.k8s.monitor.service.gpu;

import com.k8s.monitor.dto.gpu.GpuForecastAnalysis;
import com.k8s.monitor.entity.gpu.GpuAllocation; // 수정된 import
import com.k8s.monitor.repository.gpu.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * GPU 예측 분석 서비스 (수정된 버전)
 * GPU 사용량, 비용, 용량 예측 분석
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GpuForecastService {
    
    private final GpuUsageMetricsRepository metricsRepository;
    private final GpuAllocationRepository allocationRepository;
    private final GpuDeviceRepository gpuDeviceRepository;

    /**
     * GPU 사용량 예측 분석
     */
    public GpuForecastAnalysis generateUsageForecast(int forecastHours) {
        log.info("Generating GPU usage forecast for {} hours", forecastHours);
        
        // 입력 검증
        if (forecastHours <= 0 || forecastHours > 8760) { // 최대 1년
            throw new IllegalArgumentException("Forecast hours must be between 1 and 8760");
        }
        
        try {
            // 과거 데이터 수집 (예측 기간의 2배)
            LocalDateTime historicalStart = LocalDateTime.now().minusHours(forecastHours * 2);
            LocalDateTime now = LocalDateTime.now();
            
            // 시간별 사용률 트렌드 분석
            List<Object[]> hourlyTrend = metricsRepository.findHourlyUsageTrend(historicalStart);
            Map<String, Double> utilizationTrend = analyzeUtilizationTrend(hourlyTrend);
            
            // 비용 트렌드 분석
            List<GpuAllocation> recentAllocations = allocationRepository.findByAllocationTimeBetween(historicalStart, now);
            Map<String, Double> costTrend = analyzeCostTrend(recentAllocations);
            
            // 용량 분석
            CapacityAnalysis capacity = analyzeCapacity();
            
            return GpuForecastAnalysis.builder()
                .utilizationForecast24h(generateUtilizationForecast(24))
                .utilizationForecast7d(generateUtilizationForecast(24 * 7))
                .utilizationForecast30d(generateUtilizationForecast(24 * 30))
                .costForecast24h(generateCostForecast(24))
                .costForecast7d(generateCostForecast(24 * 7))
                .costForecast30d(generateCostForecast(24 * 30))
                .capacityExhaustionEta(capacity.getExhaustionEta())
                .additionalGpusNeeded(capacity.getAdditionalGpusNeeded())
                .recommendedGpuModels(capacity.getRecommendedModels())
                .utilizationTrend(determineTrend(utilizationTrend))
                .costTrend(determineTrend(costTrend))
                .demandTrend(analyzeDemandTrend(recentAllocations))
                .scalingRecommendations(generateScalingRecommendations(capacity))
                .optimizationRecommendations(generateOptimizationRecommendations())
                .forecastDate(LocalDateTime.now())
                .forecastMethod("LINEAR_REGRESSION")
                .confidence(calculateConfidence(hourlyTrend, recentAllocations))
                .build();
                
        } catch (Exception e) {
            log.error("Error generating usage forecast: {}", e.getMessage(), e);
            return createDefaultForecastAnalysis(forecastHours);
        }
    }

    /**
     * 간단한 예측 분석 생성 (fallback 메서드)
     */
    public GpuForecastAnalysis generateBasicForecast(int forecastHours) {
        log.info("Generating basic GPU forecast for {} hours", forecastHours);
        
        try {
            // 현재 상태 기반 간단한 예측
            long totalGpus = gpuDeviceRepository.count();
            long availableGpus = gpuDeviceRepository.findAvailableDevices().size();
            List<GpuAllocation> activeAllocations = allocationRepository.findActiveAllocations();
            
            double currentUtilizationRate = totalGpus > 0 ? 
                (double) (totalGpus - availableGpus) / totalGpus * 100 : 0.0;
            
            return GpuForecastAnalysis.builder()
                .utilizationForecast24h(generateSimpleUtilizationForecast(currentUtilizationRate, 24))
                .utilizationForecast7d(generateSimpleUtilizationForecast(currentUtilizationRate, 24 * 7))
                .utilizationForecast30d(generateSimpleUtilizationForecast(currentUtilizationRate, 24 * 30))
                .costForecast24h(calculateSimpleCostForecast(activeAllocations, 24))
                .costForecast7d(calculateSimpleCostForecast(activeAllocations, 24 * 7))
                .costForecast30d(calculateSimpleCostForecast(activeAllocations, 24 * 30))
                .utilizationTrend(currentUtilizationRate > 80 ? "INCREASING" : 
                                currentUtilizationRate < 20 ? "DECREASING" : "STABLE")
                .costTrend("STABLE")
                .demandTrend("STABLE")
                .scalingRecommendations(generateBasicScalingRecommendations(totalGpus, availableGpus))
                .optimizationRecommendations(generateBasicOptimizationRecommendations())
                .forecastDate(LocalDateTime.now())
                .forecastMethod("BASIC_LINEAR")
                .confidence(0.6)
                .build();
                
        } catch (Exception e) {
            log.error("Error generating basic forecast: {}", e.getMessage(), e);
            return createDefaultForecastAnalysis(forecastHours);
        }
    }

    // Private helper methods

    private Map<String, Double> analyzeUtilizationTrend(List<Object[]> hourlyTrend) {
        Map<String, Double> trend = new HashMap<>();
        
        if (hourlyTrend == null || hourlyTrend.size() < 2) {
            trend.put("slope", 0.0);
            trend.put("current", 50.0);
            return trend;
        }
        
        try {
            List<Double> utilizationValues = hourlyTrend.stream()
                .map(row -> {
                    Object gpuUtil = row[1];
                    return gpuUtil instanceof Number ? ((Number) gpuUtil).doubleValue() : 0.0;
                })
                .collect(Collectors.toList());
            
            // 선형 회귀를 사용한 트렌드 계산
            double slope = calculateLinearRegressionSlope(utilizationValues);
            double current = utilizationValues.get(utilizationValues.size() - 1);
            
            trend.put("slope", slope);
            trend.put("current", current);
            
        } catch (Exception e) {
            log.warn("Error analyzing utilization trend: {}", e.getMessage());
            trend.put("slope", 0.0);
            trend.put("current", 50.0);
        }
        
        return trend;
    }

    private Map<String, Double> analyzeCostTrend(List<GpuAllocation> allocations) {
        Map<String, Double> trend = new HashMap<>();
        
        if (allocations == null || allocations.isEmpty()) {
            trend.put("slope", 0.0);
            trend.put("current", 0.0);
            return trend;
        }
        
        try {
            // 일별 비용 계산
            Map<String, Double> dailyCosts = allocations.stream()
                .filter(allocation -> allocation.getTotalCost() != null)
                .collect(Collectors.groupingBy(
                    allocation -> allocation.getAllocationTime().toLocalDate().toString(),
                    Collectors.summingDouble(GpuAllocation::getTotalCost)
                ));
            
            if (dailyCosts.size() >= 2) {
                List<Double> costValues = new ArrayList<>(dailyCosts.values());
                double slope = calculateLinearRegressionSlope(costValues);
                double current = costValues.get(costValues.size() - 1);
                
                trend.put("slope", slope);
                trend.put("current", current);
            } else {
                double totalCost = allocations.stream()
                    .filter(allocation -> allocation.getTotalCost() != null)
                    .mapToDouble(GpuAllocation::getTotalCost)
                    .sum();
                
                trend.put("slope", 0.0);
                trend.put("current", totalCost);
            }
            
        } catch (Exception e) {
            log.warn("Error analyzing cost trend: {}", e.getMessage());
            trend.put("slope", 0.0);
            trend.put("current", 0.0);
        }
        
        return trend;
    }

    private CapacityAnalysis analyzeCapacity() {
        try {
            long totalGpus = gpuDeviceRepository.count();
            long availableGpus = gpuDeviceRepository.findAvailableDevices().size();
            long activeAllocations = allocationRepository.findActiveAllocations().size();
            
            double utilizationRate = totalGpus > 0 ? (double) (totalGpus - availableGpus) / totalGpus : 0;
            
            // 용량 고갈 예상 시점 계산
            LocalDateTime exhaustionEta = null;
            int additionalGpusNeeded = 0;
            
            if (utilizationRate > 0.8) { // 80% 이상 사용 중
                // 간단한 선형 예측 (주당 5% 증가 가정)
                double remainingCapacity = 1.0 - utilizationRate;
                int weeksToExhaustion = (int) (remainingCapacity / 0.05);
                exhaustionEta = LocalDateTime.now().plusWeeks(weeksToExhaustion);
                additionalGpusNeeded = (int) (totalGpus * 0.2); // 20% 추가 권장
            }
            
            return new CapacityAnalysis(exhaustionEta, additionalGpusNeeded, 
                determineRecommendedModels(totalGpus));
                
        } catch (Exception e) {
            log.warn("Error analyzing capacity: {}", e.getMessage());
            return new CapacityAnalysis(null, 0, "RTX4090,A100_80GB");
        }
    }

    private String determineRecommendedModels(long totalGpus) {
        if (totalGpus < 10) {
            return "RTX4090"; // 소규모 클러스터
        } else if (totalGpus < 50) {
            return "RTX4090,A100_40GB"; // 중간 규모
        } else {
            return "A100_80GB,H100_80GB"; // 대규모 클러스터
        }
    }

    private Map<String, Double> generateUtilizationForecast(int hours) {
        Map<String, Double> forecast = new HashMap<>();
        
        try {
            // 현재 사용률 기반으로 예측
            LocalDateTime since = LocalDateTime.now().minusHours(Math.min(hours, 24));
            List<Object[]> currentStats = metricsRepository.findUsageStatsByDevice(since);
            
            double avgCurrentUtilization = currentStats.stream()
                .mapToDouble(row -> {
                    Object util = row[2];
                    return util instanceof Number ? ((Number) util).doubleValue() : 0.0;
                })
                .average()
                .orElse(50.0);
            
            // 시간대별 변동 패턴 적용
            for (int i = 1; i <= Math.min(hours, 168); i += Math.max(1, hours / 24)) {
                double projectedUtilization = avgCurrentUtilization * getTimeBasedMultiplier(i);
                projectedUtilization = Math.min(100.0, Math.max(0.0, projectedUtilization));
                forecast.put("hour_" + i, projectedUtilization);
            }
            
        } catch (Exception e) {
            log.warn("Error generating utilization forecast: {}", e.getMessage());
            // 기본 예측값 생성
            for (int i = 1; i <= Math.min(hours, 24); i++) {
                forecast.put("hour_" + i, 50.0 + (Math.random() - 0.5) * 20);
            }
        }
        
        return forecast;
    }

    private Map<String, Double> generateSimpleUtilizationForecast(double currentRate, int hours) {
        Map<String, Double> forecast = new HashMap<>();
        
        // 현재 사용률 기반 간단한 예측
        for (int i = 1; i <= Math.min(hours, 24); i++) {
            double variation = (Math.random() - 0.5) * 10; // ±5% 변동
            double predicted = Math.min(100.0, Math.max(0.0, currentRate + variation));
            forecast.put("hour_" + i, predicted);
        }
        
        return forecast;
    }

    private Double generateCostForecast(int hours) {
        try {
            // 현재 시간당 비용 계산
            List<GpuAllocation> activeAllocations = allocationRepository.findActiveAllocations();
            double currentHourlyCost = activeAllocations.stream()
                .filter(allocation -> allocation.getCostPerHour() != null)
                .mapToDouble(GpuAllocation::getCostPerHour)
                .sum();
            
            // 성장률 적용 (월 3% 성장 가정)
            double growthRate = Math.pow(1.03, hours / (24.0 * 30.0));
            
            return currentHourlyCost * hours * growthRate;
            
        } catch (Exception e) {
            log.warn("Error generating cost forecast: {}", e.getMessage());
            return 0.0;
        }
    }

    private Double calculateSimpleCostForecast(List<GpuAllocation> activeAllocations, int hours) {
        try {
            double currentHourlyCost = activeAllocations.stream()
                .filter(allocation -> allocation.getCostPerHour() != null)
                .mapToDouble(GpuAllocation::getCostPerHour)
                .sum();
            
            return currentHourlyCost * hours;
            
        } catch (Exception e) {
            log.warn("Error calculating simple cost forecast: {}", e.getMessage());
            return 0.0;
        }
    }

    private String determineTrend(Map<String, Double> trendData) {
        if (trendData == null || trendData.isEmpty()) {
            return "STABLE";
        }
        
        Double slope = trendData.get("slope");
        if (slope == null) return "STABLE";
        
        if (slope > 0.1) return "INCREASING";
        if (slope < -0.1) return "DECREASING";
        return "STABLE";
    }

    private String analyzeDemandTrend(List<GpuAllocation> allocations) {
        if (allocations == null || allocations.size() < 7) {
            return "STABLE";
        }
        
        try {
            // 최근 7일과 이전 7일 비교
            LocalDateTime midPoint = LocalDateTime.now().minusDays(7);
            
            long recentAllocations = allocations.stream()
                .filter(allocation -> allocation.getAllocationTime().isAfter(midPoint))
                .count();
            
            long previousAllocations = allocations.stream()
                .filter(allocation -> allocation.getAllocationTime().isBefore(midPoint))
                .count();
            
            if (recentAllocations > previousAllocations * 1.2) return "INCREASING";
            if (recentAllocations < previousAllocations * 0.8) return "DECREASING";
            return "STABLE";
            
        } catch (Exception e) {
            log.warn("Error analyzing demand trend: {}", e.getMessage());
            return "STABLE";
        }
    }

    private List<String> generateScalingRecommendations(CapacityAnalysis capacity) {
        List<String> recommendations = new ArrayList<>();
        
        try {
            if (capacity.getAdditionalGpusNeeded() > 0) {
                recommendations.add("추가 GPU 장비 " + capacity.getAdditionalGpusNeeded() + "개 확보 권장");
                recommendations.add("고성능 모델(" + capacity.getRecommendedModels() + ") 도입 검토");
            }
            
            if (capacity.getExhaustionEta() != null) {
                recommendations.add("용량 고갈 예상: " + capacity.getExhaustionEta().toLocalDate());
            }
            
            if (recommendations.isEmpty()) {
                recommendations.add("현재 용량으로 충분합니다");
            }
            
        } catch (Exception e) {
            log.warn("Error generating scaling recommendations: {}", e.getMessage());
            recommendations.add("용량 분석 중 오류 발생");
        }
        
        return recommendations;
    }

    private List<String> generateBasicScalingRecommendations(long totalGpus, long availableGpus) {
        List<String> recommendations = new ArrayList<>();
        
        double utilizationRate = totalGpus > 0 ? (double) (totalGpus - availableGpus) / totalGpus : 0;
        
        if (utilizationRate > 0.9) {
            recommendations.add("높은 사용률: 추가 GPU 확보 검토 필요");
        } else if (utilizationRate < 0.3) {
            recommendations.add("낮은 사용률: 리소스 최적화 검토");
        } else {
            recommendations.add("적정 사용률 유지 중");
        }
        
        return recommendations;
    }

    private List<String> generateOptimizationRecommendations() {
        return Arrays.asList(
            "MIG 인스턴스 활용률 증대",
            "워크로드 스케줄링 최적화",
            "유휴 시간 활용 정책 수립",
            "비용 효율성 모니터링 강화"
        );
    }

    private List<String> generateBasicOptimizationRecommendations() {
        return Arrays.asList(
            "리소스 사용률 모니터링",
            "워크로드 분산 검토",
            "비용 최적화 분석"
        );
    }

    private double calculateConfidence(List<Object[]> hourlyTrend, List<GpuAllocation> allocations) {
        try {
            double confidence = 0.5; // 기본 신뢰도
            
            // 데이터 양에 따른 신뢰도 조정
            if (hourlyTrend != null && hourlyTrend.size() > 48) {
                confidence += 0.2;
            }
            
            if (allocations != null && allocations.size() > 100) {
                confidence += 0.2;
            }
            
            return Math.min(1.0, confidence);
            
        } catch (Exception e) {
            return 0.5;
        }
    }

    private double calculateLinearRegressionSlope(List<Double> values) {
        if (values == null || values.size() < 2) return 0.0;
        
        try {
            int n = values.size();
            double sumX = n * (n - 1) / 2.0; // 0, 1, 2, ... n-1의 합
            double sumY = values.stream().mapToDouble(Double::doubleValue).sum();
            double sumXY = 0.0;
            double sumX2 = 0.0;
            
            for (int i = 0; i < n; i++) {
                sumXY += i * values.get(i);
                sumX2 += i * i;
            }
            
            return (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
            
        } catch (Exception e) {
            log.warn("Error calculating linear regression slope: {}", e.getMessage());
            return 0.0;
        }
    }

    private double getTimeBasedMultiplier(int hour) {
        // 시간대별 사용률 변동 패턴 (업무시간 vs 휴무시간)
        int hourOfDay = hour % 24;
        
        if (hourOfDay >= 9 && hourOfDay <= 18) {
            return 1.2; // 업무시간 20% 증가
        } else if (hourOfDay >= 22 || hourOfDay <= 6) {
            return 0.7; // 심야시간 30% 감소
        } else {
            return 1.0; // 기본
        }
    }

    // 기본값 생성 메서드
    private GpuForecastAnalysis createDefaultForecastAnalysis(int hours) {
        return GpuForecastAnalysis.builder()
            .utilizationForecast24h(new HashMap<>())
            .utilizationForecast7d(new HashMap<>())
            .utilizationForecast30d(new HashMap<>())
            .costForecast24h(0.0)
            .costForecast7d(0.0)
            .costForecast30d(0.0)
            .utilizationTrend("UNKNOWN")
            .costTrend("UNKNOWN")
            .demandTrend("UNKNOWN")
            .scalingRecommendations(Arrays.asList("데이터 부족으로 분석 불가"))
            .optimizationRecommendations(Arrays.asList("기본 최적화 권장"))
            .forecastDate(LocalDateTime.now())
            .forecastMethod("DEFAULT")
            .confidence(0.1)
            .build();
    }

    // Inner class for capacity analysis
    private static class CapacityAnalysis {
        private final LocalDateTime exhaustionEta;
        private final int additionalGpusNeeded;
        private final String recommendedModels;

        public CapacityAnalysis(LocalDateTime exhaustionEta, int additionalGpusNeeded, String recommendedModels) {
            this.exhaustionEta = exhaustionEta;
            this.additionalGpusNeeded = additionalGpusNeeded;
            this.recommendedModels = recommendedModels;
        }

        public LocalDateTime getExhaustionEta() { return exhaustionEta; }
        public int getAdditionalGpusNeeded() { return additionalGpusNeeded; }
        public String getRecommendedModels() { return recommendedModels; }
    }
}