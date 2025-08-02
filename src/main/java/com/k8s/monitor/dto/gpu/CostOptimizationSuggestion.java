package com.k8s.monitor.dto.gpu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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