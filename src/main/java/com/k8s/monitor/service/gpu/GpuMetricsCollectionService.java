package com.k8s.monitor.service.gpu;

import com.k8s.monitor.entity.gpu.*;
import com.k8s.monitor.repository.gpu.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * GPU 메트릭 수집 서비스
 * nvidia-smi를 통한 GPU 메트릭 수집 및 저장
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GpuMetricsCollectionService {
    
    private final GpuUsageMetricsRepository metricsRepository;
    private final GpuDeviceRepository gpuDeviceRepository;
    private final MigInstanceRepository migInstanceRepository;

    /**
     * GPU 메트릭 수집 (스케줄러)
     */
    @Scheduled(fixedRate = 30000) // 30초마다 실행
    @Transactional
    public void collectGpuMetrics() {
        try {
            log.debug("Starting GPU metrics collection");
            
            List<GpuDevice> activeDevices = gpuDeviceRepository.findByDeviceStatus("ACTIVE");
            List<GpuDevice> migEnabledDevices = gpuDeviceRepository.findByDeviceStatus("MIG_ENABLED");
            
            // 일반 GPU 메트릭 수집
            for (GpuDevice device : activeDevices) {
                collectDeviceMetrics(device);
            }
            
            // MIG 인스턴스 메트릭 수집
            for (GpuDevice device : migEnabledDevices) {
                List<MigInstance> instances = migInstanceRepository.findByDeviceDeviceId(device.getDeviceId());
                for (MigInstance instance : instances) {
                    if ("ACTIVE".equals(instance.getInstanceStatus())) {
                        collectMigInstanceMetrics(instance);
                    }
                }
            }
            
            log.debug("GPU metrics collection completed");
            
        } catch (Exception e) {
            log.error("Error during GPU metrics collection: {}", e.getMessage(), e);
        }
    }

    /**
     * 수동 GPU 메트릭 수집 트리거
     */
    public void collectGpuMetrics() {
        collectGpuMetrics();
    }

    /**
     * GPU 사용량 통계 조회
     */
    public Map<String, Object> getGpuUsageStatistics(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        List<Object[]> usageStatsByDevice = metricsRepository.findUsageStatsByDevice(since);
        List<Object[]> usageStatsByModel = metricsRepository.findUsageStatsByModel(since);
        List<Object[]> usageStatsByNode = metricsRepository.findUsageStatsByNode(since);
        List<Object[]> hourlyTrend = metricsRepository.findHourlyUsageTrend(since);
        
        Map<String, Object> deviceStats = usageStatsByDevice.stream()
            .collect(Collectors.toMap(
                row -> (String) row[0],
                row -> Map.of(
                    "modelName", (String) row[1],
                    "avgGpuUtilization", ((Number) row[2]).doubleValue(),
                    "avgMemoryUtilization", ((Number) row[3]).doubleValue(),
                    "avgTemperature", ((Number) row[4]).doubleValue()
                )
            ));
        
        Map<String, Object> modelStats = usageStatsByModel.stream()
            .collect(Collectors.toMap(
                row -> (String) row[0],
                row -> Map.of(
                    "deviceCount", ((Number) row[1]).intValue(),
                    "avgGpuUtilization", ((Number) row[2]).doubleValue(),
                    "avgMemoryUtilization", ((Number) row[3]).doubleValue(),
                    "avgTemperature", ((Number) row[4]).doubleValue()
                )
            ));
        
        Map<String, Object> nodeStats = usageStatsByNode.stream()
            .collect(Collectors.toMap(
                row -> (String) row[0],
                row -> Map.of(
                    "deviceCount", ((Number) row[1]).intValue(),
                    "avgGpuUtilization", ((Number) row[2]).doubleValue(),
                    "avgMemoryUtilization", ((Number) row[3]).doubleValue(),
                    "avgTemperature", ((Number) row[4]).doubleValue()
                )
            ));
        
        List<Map<String, Object>> trendData = hourlyTrend.stream()
            .map(row -> Map.of(
                "hour", ((Number) row[0]).intValue(),
                "avgGpuUtilization", ((Number) row[1]).doubleValue(),
                "avgMemoryUtilization", ((Number) row[2]).doubleValue(),
                "avgTemperature", ((Number) row[3]).doubleValue()
            ))
            .collect(Collectors.toList());
        
        return Map.of(
            "timeRange", hours + " hours",
            "deviceStatistics", deviceStats,
            "modelStatistics", modelStats,
            "nodeStatistics", nodeStats,
            "hourlyTrend", trendData,
            "lastUpdated", LocalDateTime.now()
        );
    }

    /**
     * 과열 알람 조회
     */
    public List<Map<String, Object>> getOverheatingAlerts() {
        LocalDateTime since = LocalDateTime.now().minusHours(1);
        List<GpuUsageMetrics> overheatingMetrics = metricsRepository.findOverheatingMetrics(85.0, since);
        
        return overheatingMetrics.stream()
            .map(metric -> Map.of(
                "deviceId", metric.getDevice() != null ? metric.getDevice().getDeviceId() : "unknown",
                "migId", metric.getMigInstance() != null ? metric.getMigInstance().getMigId() : null,
                "temperature", metric.getTemperatureC(),
                "timestamp", metric.getTimestamp(),
                "severity", getTemperatureSeverity(metric.getTemperatureC())
            ))
            .collect(Collectors.toList());
    }

    /**
     * 오래된 메트릭 데이터 정리 (스케줄러)
     */
    @Scheduled(cron = "0 0 2 * * *") // 매일 새벽 2시 실행
    @Transactional
    public void cleanupOldMetrics() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30); // 30일 이전 데이터 삭제
        int deletedCount = metricsRepository.deleteOldMetrics(cutoff);
        
        if (deletedCount > 0) {
            log.info("Cleaned up {} old GPU metrics records", deletedCount);
        }
    }

    // Private helper methods
    
    private void collectDeviceMetrics(GpuDevice device) {
        try {
            Map<String, Object> metrics = executeNvidiaSmi(device.getDeviceIndex());
            
            if (metrics != null && !metrics.isEmpty()) {
                GpuUsageMetrics metricsEntity = GpuUsageMetrics.builder()
                    .device(device)
                    .timestamp(LocalDateTime.now())
                    .gpuUtilizationPct(getDoubleValue(metrics, "utilization.gpu"))
                    .memoryUsedMb(getLongValue(metrics, "memory.used"))
                    .memoryTotalMb(getLongValue(metrics, "memory.total"))
                    .memoryUtilizationPct(getDoubleValue(metrics, "utilization.memory"))
                    .temperatureC(getDoubleValue(metrics, "temperature.gpu"))
                    .powerDrawW(getDoubleValue(metrics, "power.draw"))
                    .fanSpeedPct(getDoubleValue(metrics, "fan.speed"))
                    .clockGraphicsMhz(getIntegerValue(metrics, "clocks.gr"))
                    .clockMemoryMhz(getIntegerValue(metrics, "clocks.mem"))
                    .collectionSource("nvidia-smi")
                    .build();
                
                metricsRepository.save(metricsEntity);
                
                // GPU 장비의 현재 상태 업데이트
                device.setCurrentTempC(metricsEntity.getTemperatureC());
                device.setCurrentPowerW(metricsEntity.getPowerDrawW());
                gpuDeviceRepository.save(device);
            }
            
        } catch (Exception e) {
            log.warn("Failed to collect metrics for device {}: {}", device.getDeviceId(), e.getMessage());
        }
    }

    private void collectMigInstanceMetrics(MigInstance instance) {
        try {
            Map<String, Object> metrics = executeNvidiaSmiForMig(
                instance.getDevice().getDeviceIndex(), 
                instance.getInstanceId()
            );
            
            if (metrics != null && !metrics.isEmpty()) {
                GpuUsageMetrics metricsEntity = GpuUsageMetrics.builder()
                    .device(instance.getDevice())
                    .migInstance(instance)
                    .timestamp(LocalDateTime.now())
                    .gpuUtilizationPct(getDoubleValue(metrics, "utilization.gpu"))
                    .memoryUsedMb(getLongValue(metrics, "memory.used"))
                    .memoryTotalMb(getLongValue(metrics, "memory.total"))
                    .memoryUtilizationPct(getDoubleValue(metrics, "utilization.memory"))
                    .temperatureC(getDoubleValue(metrics, "temperature.gpu"))
                    .powerDrawW(getDoubleValue(metrics, "power.draw"))
                    .collectionSource("nvidia-smi-mig")
                    .build();
                
                metricsRepository.save(metricsEntity);
            }
            
        } catch (Exception e) {
            log.warn("Failed to collect metrics for MIG instance {}: {}", instance.getMigId(), e.getMessage());
        }
    }

    private Map<String, Object> executeNvidiaSmi(Integer deviceIndex) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "nvidia-smi",
                "--query-gpu=utilization.gpu,utilization.memory,memory.used,memory.total,temperature.gpu,power.draw,fan.speed,clocks.gr,clocks.mem",
                "--format=csv,noheader,nounits",
                "--id=" + deviceIndex
            );
            
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            
            if (line != null) {
                return parseNvidiaSmiOutput(line);
            }
            
        } catch (Exception e) {
            log.error("Error executing nvidia-smi for device {}: {}", deviceIndex, e.getMessage());
        }
        
        return null;
    }

    private Map<String, Object> executeNvidiaSmiForMig(Integer deviceIndex, Integer migInstanceId) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "nvidia-smi",
                "--query-gpu=utilization.gpu,utilization.memory,memory.used,memory.total",
                "--format=csv,noheader,nounits",
                "--id=" + deviceIndex + ":" + migInstanceId
            );
            
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            
            if (line != null) {
                return parseMigNvidiaSmiOutput(line);
            }
            
        } catch (Exception e) {
            log.error("Error executing nvidia-smi for MIG instance {}:{}: {}", deviceIndex, migInstanceId, e.getMessage());
        }
        
        return null;
    }

    private Map<String, Object> parseNvidiaSmiOutput(String line) {
        String[] values = line.split(",");
        Map<String, Object> metrics = new HashMap<>();
        
        if (values.length >= 9) {
            metrics.put("utilization.gpu", parseDouble(values[0].trim()));
            metrics.put("utilization.memory", parseDouble(values[1].trim()));
            metrics.put("memory.used", parseLong(values[2].trim()));
            metrics.put("memory.total", parseLong(values[3].trim()));
            metrics.put("temperature.gpu", parseDouble(values[4].trim()));
            metrics.put("power.draw", parseDouble(values[5].trim()));
            metrics.put("fan.speed", parseDouble(values[6].trim()));
            metrics.put("clocks.gr", parseInteger(values[7].trim()));
            metrics.put("clocks.mem", parseInteger(values[8].trim()));
        }
        
        return metrics;
    }

    private Map<String, Object> parseMigNvidiaSmiOutput(String line) {
        String[] values = line.split(",");
        Map<String, Object> metrics = new HashMap<>();
        
        if (values.length >= 4) {
            metrics.put("utilization.gpu", parseDouble(values[0].trim()));
            metrics.put("utilization.memory", parseDouble(values[1].trim()));
            metrics.put("memory.used", parseLong(values[2].trim()));
            metrics.put("memory.total", parseLong(values[3].trim()));
        }
        
        return metrics;
    }

    private String getTemperatureSeverity(Double temperature) {
        if (temperature == null) return "UNKNOWN";
        if (temperature >= 90) return "CRITICAL";
        if (temperature >= 85) return "WARNING";
        return "NORMAL";
    }

    // Utility methods for safe parsing
    private Double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Double ? (Double) value : null;
    }

    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Long ? (Long) value : null;
    }

    private Integer getIntegerValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Integer ? (Integer) value : null;
    }

    private Double parseDouble(String value) {
        try {
            return "N/A".equals(value) ? null : Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLong(String value) {
        try {
            return "N/A".equals(value) ? null : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInteger(String value) {
        try {
            return "N/A".equals(value) ? null : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}