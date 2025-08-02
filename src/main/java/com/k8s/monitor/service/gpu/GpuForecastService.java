package com.k8s.monitor.service.gpu;

import com.k8s.monitor.dto.gpu.GpuForecastAnalysis;
import com.k8s.monitor.repository.gpu.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * GPU 예측 분석 서비스
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
            .confidence(0.75)
            .build();
    }

    // Private helper methods
    
    private Map<String, Double> analyzeUtilizationTrend(List<Object[]> hourlyTrend) {
        Map<String, Double> trend = new HashMap<>();
        
        if (hourlyTrend.size() >= 2) {
            List<Double> utilizationValues = hourlyTrend.stream()
                .map(row -> ((Number) row[1]).doubleValue())
                .collect(Collectors.toList());
            
            // 선형 회귀를 사용한 트렌드 계산
            double slope = calculateLinearRegressionSlope(utilizationValues);
            trend.put("slope", slope);
            trend.put("current", utilizationValues.get(utilizationValues.size() - 1));
        }
        
        return trend;
    }

    private Map<String, Double> analyzeCostTrend(List<GpuAllocation> allocations) {
        Map<String, Double> trend = new HashMap<>();
        
        if (!allocations.isEmpty()) {
            // 일별 비용 계산
            Map<String, Double> dailyCosts = allocations.stream()
                .filter(allocation -> allocation.getTotalCost() != null)
                .collect(Collectors.groupingBy(
                    allocation -> allocation.getAllocationTime().toLocalDate().toString(),
                    Collectors.summingDouble(GpuAllocation::getTotalCost)
                ));
            
            List<Double> costValues = new ArrayList<>(dailyCosts.values());
            if (costValues.size() >= 2) {
                double slope = calculateLinearRegressionSlope(costValues);
                trend.put("slope", slope);
                trend.put("current", costValues.get(costValues.size() - 1));
            }
        }
        
        return trend;
    }

    private CapacityAnalysis analyzeCapacity() {
        long totalGpus = gpuDeviceRepository.count();
        long availableGpus = gpuDeviceRepository.findAvailableDevices().size();
        long activeAllocations = allocationRepository.findActiveAllocations().size();
        
        double utilizationRate = totalGpus > 0 ? (double) (totalGpus - availableGpus) / totalGpus : 0;
        
        // 용량 고갈 예상 시점 계산 (현재 트렌드 기반)
        LocalDateTime exhaustionEta = null;
        int additionalGpusNeeded = 0;
        
        if (utilizationRate > 0.8) { // 80% 이상 사용 중
            // 간단한 선형 예측
            int daysToExhaustion = (int) ((1.0 - utilizationRate) / 0.01 * 7); // 주당 1% 증가 가정
            exhaustionEta = LocalDateTime.now().plusDays(daysToExhaustion);
            additionalGpusNeeded = (int) (totalGpus * 0.2); // 20% 추가 권장
        }
        
        return new CapacityAnalysis(exhaustionEta, additionalGpusNeeded, "RTX4090,A100_80GB");
    }

    private Map<String, Double> generateUtilizationForecast(int hours) {
        Map<String, Double> forecast = new HashMap<>();
        
        // 현재 사용률 기반으로 예측
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        List<Object[]> currentStats = metricsRepository.findUsageStatsByDevice(since);
        
        double avgCurrentUtilization = currentStats.stream()
            .mapToDouble(row -> ((Number) row[2]).doubleValue())
            .average()
            .orElse(50.0);
        
        // 시간대별 변동 패턴 적용
        for (int i = 1; i <= Math.min(hours, 168); i += Math.max(1, hours / 24)) {
            double projectedUtilization = avgCurrentUtilization * getTimeBasedMultiplier(i);
            forecast.put("hour_" + i, Math.min(100.0, Math.max(0.0, projectedUtilization)));
        }
        
        return forecast;
    }

    private Double generateCostForecast(int hours) {
        // 현재 시간당 비용 계산
        List<GpuAllocation> activeAllocations = allocationRepository.findActiveAllocations();
        double currentHourlyCost = activeAllocations.stream()
            .filter(allocation -> allocation.getCostPerHour() != null)
            .mapToDouble(GpuAllocation::getCostPerHour)
            .sum();
        
        // 성장률 적용 (월 5% 성장 가정)
        double growthRate = Math.pow(1.05, hours / (24.0 * 30.0));
        
        return currentHourlyCost * hours * growthRate;
    }

    private String determineTrend(Map<String, Double> trendData) {
        Double slope = trendData.get("slope");
        if (slope == null) return "STABLE";
        
        if (slope > 0.1) return "INCREASING";
        if (slope < -0.1) return "DECREASING";
        return "STABLE";
    }

    private String analyzeDemandTrend(List<GpuAllocation> allocations) {
        if (allocations.size() < 7) return "STABLE";
        
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
    }

    private List<String> generateScalingRecommendations(CapacityAnalysis capacity) {
        List<String> recommendations = new ArrayList<>();
        
        if (capacity.getAdditionalGpusNeeded() > 0) {
            recommendations.add("추가 GPU 장비 " + capacity.getAdditionalGpusNeeded() + "개 확보 권장");
            recommendations.add("고성능 모델(" + capacity.getRecommendedModels() + ") 도입 검토");
        }
        
        if (capacity.getExhaustionEta() != null) {
            recommendations.add("용량 고갈 예상: " + capacity.getExhaustionEta().toLocalDate());
        }
        
        return recommendations;
    }

    private List<String> generateOptimizationRecommendations() {
        return Arrays.asList(
            "MIG 인스턴스 활용률 증대",
            "워크로드 스케줄링 최적화",
            "유휴 시간 활용 정책 수립"
        );
    }

    private double calculateLinearRegressionSlope(List<Double> values) {
        if (values.size() < 2) return 0.0;
        
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