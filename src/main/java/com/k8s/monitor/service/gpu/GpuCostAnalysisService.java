package com.k8s.monitor.service.gpu;

import com.k8s.monitor.dto.gpu.*;
import com.k8s.monitor.entity.gpu.*;
import com.k8s.monitor.repository.gpu.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * GPU 비용 분석 서비스
 * GPU 사용 비용 계산 및 최적화 제안
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GpuCostAnalysisService {
    
    private final GpuAllocationRepository allocationRepository;
    private final GpuDeviceRepository gpuDeviceRepository;
    private final MigInstanceRepository migInstanceRepository;
    
    @Value("${gpu.management.cost.currency:USD}")
    private String currency;
    
    @Value("#{${gpu.management.cost.default-rates}}")
    private Map<String, Double> defaultRates;

    /**
     * 시간당 비용 계산
     */
    public Double calculateCostPerHour(String resourceType, String resourceId) {
        if ("MIG_INSTANCE".equals(resourceType)) {
            return calculateMigInstanceCostPerHour(resourceId);
        } else {
            return calculateGpuDeviceCostPerHour(resourceId);
        }
    }

    /**
     * GPU 비용 분석 생성
     */
    public GpuCostAnalysis generateCostAnalysis(int days) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        LocalDateTime endDate = LocalDateTime.now();
        
        List<GpuAllocation> allocations = allocationRepository.findByAllocationTimeBetween(startDate, endDate);
        
        // 기간별 비용 계산
        Double totalCost = allocations.stream()
            .filter(allocation -> allocation.getTotalCost() != null)
            .mapToDouble(GpuAllocation::getTotalCost)
            .sum();
        
        Double dailyCost = totalCost / days;
        Double weeklyCost = dailyCost * 7;
        Double monthlyCost = dailyCost * 30;
        Double quarterlyCost = monthlyCost * 3;
        
        // 분류별 비용 분석
        Map<String, Double> costByNamespace = allocations.stream()
            .filter(allocation -> allocation.getTotalCost() != null)
            .collect(Collectors.groupingBy(
                GpuAllocation::getNamespace,
                Collectors.summingDouble(GpuAllocation::getTotalCost)
            ));
        
        Map<String, Double> costByTeam = allocations.stream()
            .filter(allocation -> allocation.getTeamId() != null && allocation.getTotalCost() != null)
            .collect(Collectors.groupingBy(
                GpuAllocation::getTeamId,
                Collectors.summingDouble(GpuAllocation::getTotalCost)
            ));
        
        Map<String, Double> costByWorkloadType = allocations.stream()
            .filter(allocation -> allocation.getWorkloadType() != null && allocation.getTotalCost() != null)
            .collect(Collectors.groupingBy(
                GpuAllocation::getWorkloadType,
                Collectors.summingDouble(GpuAllocation::getTotalCost)
            ));
        
        // 최적화 제안 생성
        List<CostOptimizationSuggestion> suggestions = generateOptimizationSuggestions(allocations);
        Double potentialSavings = suggestions.stream()
            .mapToDouble(CostOptimizationSuggestion::getPotentialSavings)
            .sum();
        
        return GpuCostAnalysis.builder()
            .dailyCost(dailyCost)
            .weeklyCost(weeklyCost)
            .monthlyCost(monthlyCost)
            .quarterlyCost(quarterlyCost)
            .costByNamespace(costByNamespace)
            .costByTeam(costByTeam)
            .costByWorkloadType(costByWorkloadType)
            .optimizationSuggestions(suggestions)
            .potentialMonthlySavings(potentialSavings)
            .analysisDate(LocalDateTime.now())
            .analysisTimeRange(days + " days")
            .build();
    }

    /**
     * 최적화 제안 생성
     */
    public List<CostOptimizationSuggestion> generateOptimizationSuggestions(List<GpuAllocation> allocations) {
        List<CostOptimizationSuggestion> suggestions = new ArrayList<>();
        
        // MIG 사용 권장
        suggestions.addAll(generateMigUsageSuggestions(allocations));
        
        // 장기간 사용 중인 할당 최적화
        suggestions.addAll(generateLongRunningOptimizations(allocations));
        
        // 유휴 시간 최적화
        suggestions.addAll(generateIdleTimeOptimizations());
        
        return suggestions;
    }

    // Private helper methods
    
    private Double calculateMigInstanceCostPerHour(String migId) {
        return migInstanceRepository.findById(migId)
            .map(instance -> {
                String modelId = instance.getDevice().getModel().getModelId();
                Double baseCost = defaultRates.getOrDefault(modelId, 1.0);
                Double migDiscount = 0.7; // MIG 30% 할인
                Double performanceRatio = instance.getProfile().getPerformanceRatio();
                
                return baseCost * performanceRatio * migDiscount;
            })
            .orElse(0.0);
    }

    private Double calculateGpuDeviceCostPerHour(String deviceId) {
        return gpuDeviceRepository.findById(deviceId)
            .map(device -> {
                String modelId = device.getModel().getModelId();
                return defaultRates.getOrDefault(modelId, 1.0);
            })
            .orElse(0.0);
    }

    private List<CostOptimizationSuggestion> generateMigUsageSuggestions(List<GpuAllocation> allocations) {
        List<CostOptimizationSuggestion> suggestions = new ArrayList<>();
        
        // 전체 GPU 할당 중 MIG로 대체 가능한 것들 찾기
        List<GpuAllocation> fullGpuAllocations = allocations.stream()
            .filter(allocation -> "FULL_GPU".equals(allocation.getResourceType()))
            .filter(allocation -> allocation.getRequestedMemoryGb() != null && allocation.getRequestedMemoryGb() <= 20)
            .collect(Collectors.toList());
        
        if (!fullGpuAllocations.isEmpty()) {
            double potentialSavings = fullGpuAllocations.stream()
                .mapToDouble(allocation -> (allocation.getTotalCost() != null ? allocation.getTotalCost() : 0) * 0.3)
                .sum();
            
            suggestions.add(CostOptimizationSuggestion.builder()
                .suggestionType("RIGHTSIZING")
                .title("MIG 인스턴스 사용 권장")
                .description("메모리 요구사항이 20GB 이하인 워크로드는 MIG 인스턴스를 사용하여 비용을 절약할 수 있습니다.")
                .targetResource("FULL_GPU allocations with <= 20GB memory requirement")
                .currentMonthlyCost(potentialSavings / 0.3)
                .optimizedMonthlyCost(potentialSavings / 0.3 * 0.7)
                .potentialSavings(potentialSavings)
                .priority("HIGH")
                .implementation("워크로드 요구사항을 분석하여 적절한 MIG 프로필 선택")
                .impact("30% 비용 절감 예상")
                .build());
        }
        
        return suggestions;
    }

    private List<CostOptimizationSuggestion> generateLongRunningOptimizations(List<GpuAllocation> allocations) {
        List<CostOptimizationSuggestion> suggestions = new ArrayList<>();
        
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);
        List<GpuAllocation> longRunningAllocations = allocations.stream()
            .filter(allocation -> "ALLOCATED".equals(allocation.getStatus()))
            .filter(allocation -> allocation.getAllocationTime().isBefore(threshold))
            .collect(Collectors.toList());
        
        if (!longRunningAllocations.isEmpty()) {
            double totalCost = longRunningAllocations.stream()
                .mapToDouble(allocation -> allocation.getCostPerHour() != null ? allocation.getCostPerHour() * 24 * 30 : 0)
                .sum();
            
            suggestions.add(CostOptimizationSuggestion.builder()
                .suggestionType("SCHEDULING")
                .title("장기 실행 할당 검토")
                .description("7일 이상 지속되는 할당에 대한 검토가 필요합니다.")
                .targetResource(longRunningAllocations.size() + " long-running allocations")
                .currentMonthlyCost(totalCost)
                .optimizedMonthlyCost(totalCost * 0.8)
                .potentialSavings(totalCost * 0.2)
                .priority("MEDIUM")
                .implementation("할당 기간 재검토 및 자동 해제 정책 적용")
                .impact("20% 비용 절감 가능")
                .build());
        }
        
        return suggestions;
    }

    private List<CostOptimizationSuggestion> generateIdleTimeOptimizations() {
        List<CostOptimizationSuggestion> suggestions = new ArrayList<>();
        
        // 유휴 GPU 장비 확인
        List<GpuDevice> availableDevices = gpuDeviceRepository.findAvailableDevices();
        if (availableDevices.size() > 2) { // 2개 이상의 유휴 GPU가 있을 때
            double idleCost = availableDevices.stream()
                .mapToDouble(device -> defaultRates.getOrDefault(device.getModel().getModelId(), 1.0) * 24 * 30)
                .sum();
            
            suggestions.add(CostOptimizationSuggestion.builder()
                .suggestionType("TERMINATION")
                .title("유휴 GPU 장비 최적화")
                .description("현재 사용되지 않는 GPU 장비들이 있습니다.")
                .targetResource(availableDevices.size() + " idle GPU devices")
                .currentMonthlyCost(idleCost)
                .optimizedMonthlyCost(idleCost * 0.5)
                .potentialSavings(idleCost * 0.5)
                .priority("LOW")
                .implementation("유휴 장비 일부를 절전 모드로 전환")
                .impact("50% 절전 비용 절감")
                .build());
        }
        
        return suggestions;
    }
}