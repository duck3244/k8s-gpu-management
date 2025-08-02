package com.k8s.monitor.dto.gpu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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