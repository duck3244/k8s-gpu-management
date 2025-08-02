package com.k8s.monitor.controller.gpu;

import com.k8s.monitor.dto.gpu.CostOptimizationSuggestion;
import com.k8s.monitor.dto.gpu.GpuCostAnalysis;
import com.k8s.monitor.service.gpu.GpuCostAnalysisService; // 추가 필요
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/gpu/cost")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class GpuCostController {
    
    // 올바른 의존성 주입
    private final GpuCostAnalysisService costAnalysisService;

    /**
     * GPU 비용 분석 조회
     */
    @GetMapping("/analysis")
    public ResponseEntity<GpuCostAnalysis> getGpuCostAnalysis(
            @RequestParam(defaultValue = "30") int days) {
        log.info("Fetching GPU cost analysis for last {} days", days);
        
        try {
            // 서비스 메서드 위임 - 중복 구현 제거
            GpuCostAnalysis analysis = costAnalysisService.generateCostAnalysis(days);
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            log.error("Error fetching GPU cost analysis: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 비용 최적화 제안 조회
     */
    @GetMapping("/optimization")
    public ResponseEntity<List<CostOptimizationSuggestion>> getCostOptimizationSuggestions() {
        log.info("Fetching cost optimization suggestions");
        
        try {
            // 서비스 메서드 위임
            List<CostOptimizationSuggestion> suggestions = 
                costAnalysisService.generateOptimizationSuggestions(null);
            return ResponseEntity.ok(suggestions);
        } catch (Exception e) {
            log.error("Error fetching cost optimization suggestions: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
     
}