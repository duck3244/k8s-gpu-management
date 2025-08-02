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
 * GPU 메트릭 수집 서비스 (수정된 버전)
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
     * GPU 메트릭 수집 (스케줄러) - 메서드명 변경으로 중복 해결
     */
    @Scheduled(fixedRate = 30000) // 30초마다 실행
    @Transactional
    public void collectGpuMetricsScheduled() {
        try {
            log.debug("Starting scheduled GPU metrics collection");
            
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
     * 수동 GPU 메트릭 수집 트리거 - 다른 메서드명 사용
     */
    public void triggerMetricsCollection() {
        log.info("Manual GPU metrics collection triggered");
        collectGpuMetricsScheduled(); // 스케줄 메서드 재사용
    }

    /**
     * GPU 사용량 통계 조회
     */
    public Map<String, Object> getGpuUsageStatistics(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        try {
            List<Object[]> usageStatsByDevice = metricsRepository.findUsageStatsByDevice(since);
            List<Object[]> usageStatsByModel = metricsRepository.findUsageStatsByModel(since);
            List<Object[]> usageStatsByNode = metricsRepository.findUsageStatsByNode(since);
            List<Object[]> hourlyTrend = metricsRepository.findHourlyUsageTrend(since);
            
            Map<String, Object> deviceStats = convertDeviceStats(usageStatsByDevice);
            Map<String, Object> modelStats = convertModelStats(usageStatsByModel);
            Map<String, Object> nodeStats = convertNodeStats(usageStatsByNode);
            List<Map<String, Object>> trendData = convertHourlyTrend(hourlyTrend);
            
            return Map.of(
                "timeRange", hours + " hours",
                "deviceStatistics", deviceStats,
                "modelStatistics", modelStats,
                "nodeStatistics", nodeStats,
                "hourlyTrend", trendData,
                "lastUpdated", LocalDateTime.now()
            );
            
        } catch (Exception e) {
            log.error("Error getting GPU usage statistics: {}", e.getMessage(), e);
            return createEmptyStatistics(hours);
        }
    }

    /**
     * 과열 알람 조회
     */
    public List<Map<String, Object>> getOverheatingAlerts() {
        LocalDateTime since = LocalDateTime.now().minusHours(1);
        
        try {
            List<GpuUsageMetrics> overheatingMetrics = metricsRepository.findOverheatingMetrics(85.0, since);
            
            return convertOverheatingMetrics(overheatingMetrics);
            
        } catch (Exception e) {
            log.error("Error getting overheating alerts: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 오래된 메트릭 데이터 정리 (스케줄러)
     */
    @Scheduled(cron = "0 0 2 * * *") // 매일 새벽 2시 실행
    @Transactional
    public void cleanupOldMetrics() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30); // 30일 이전 데이터 삭제
        
        try {
            int deletedCount = metricsRepository.deleteOldMetrics(cutoff);
            
            if (deletedCount > 0) {
                log.info("Cleaned up {} old GPU metrics records", deletedCount);
            }
        } catch (Exception e) {
            log.error("Error cleaning up old metrics: {}", e.getMessage(), e);
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

    // 통계 변환 메서드들

    private Map<String, Object> convertDeviceStats(List<Object[]> usageStatsByDevice) {
        Map<String, Object> deviceStats = new HashMap<>();
        
        try {
            for (Object[] row : usageStatsByDevice) {
                if (row.length >= 5) {
                    String deviceId = (String) row[0];
                    String modelName = (String) row[1];
                    Double avgGpuUtilization = getDoubleFromRow(row, 2);
                    Double avgMemoryUtilization = getDoubleFromRow(row, 3);
                    Double avgTemperature = getDoubleFromRow(row, 4);
                    
                    Map<String, Object> stats = new HashMap<>();
                    stats.put("modelName", modelName);
                    stats.put("avgGpuUtilization", avgGpuUtilization);
                    stats.put("avgMemoryUtilization", avgMemoryUtilization);
                    stats.put("avgTemperature", avgTemperature);
                    
                    deviceStats.put(deviceId, stats);
                }
            }
        } catch (Exception e) {
            log.warn("Error converting device stats: {}", e.getMessage());
        }
        
        return deviceStats;
    }

    private Map<String, Object> convertModelStats(List<Object[]> usageStatsByModel) {
        Map<String, Object> modelStats = new HashMap<>();
        
        try {
            for (Object[] row : usageStatsByModel) {
                if (row.length >= 5) {
                    String modelName = (String) row[0];
                    Integer deviceCount = getIntegerFromRow(row, 1);
                    Double avgGpuUtilization = getDoubleFromRow(row, 2);
                    Double avgMemoryUtilization = getDoubleFromRow(row, 3);
                    Double avgTemperature = getDoubleFromRow(row, 4);
                    
                    Map<String, Object> stats = new HashMap<>();
                    stats.put("deviceCount", deviceCount);
                    stats.put("avgGpuUtilization", avgGpuUtilization);
                    stats.put("avgMemoryUtilization", avgMemoryUtilization);
                    stats.put("avgTemperature", avgTemperature);
                    
                    modelStats.put(modelName, stats);
                }
            }
        } catch (Exception e) {
            log.warn("Error converting model stats: {}", e.getMessage());
        }
        
        return modelStats;
    }

    private Map<String, Object> convertNodeStats(List<Object[]> usageStatsByNode) {
        Map<String, Object> nodeStats = new HashMap<>();
        
        try {
            for (Object[] row : usageStatsByNode) {
                if (row.length >= 5) {
                    String nodeName = (String) row[0];
                    Integer deviceCount = getIntegerFromRow(row, 1);
                    Double avgGpuUtilization = getDoubleFromRow(row, 2);
                    Double avgMemoryUtilization = getDoubleFromRow(row, 3);
                    Double avgTemperature = getDoubleFromRow(row, 4);
                    
                    Map<String, Object> stats = new HashMap<>();
                    stats.put("deviceCount", deviceCount);
                    stats.put("avgGpuUtilization", avgGpuUtilization);
                    stats.put("avgMemoryUtilization", avgMemoryUtilization);
                    stats.put("avgTemperature", avgTemperature);
                    
                    nodeStats.put(nodeName, stats);
                }
            }
        } catch (Exception e) {
            log.warn("Error converting node stats: {}", e.getMessage());
        }
        
        return nodeStats;
    }

    private List<Map<String, Object>> convertHourlyTrend(List<Object[]> hourlyTrend) {
        List<Map<String, Object>> trendData = new ArrayList<>();
        
        try {
            for (Object[] row : hourlyTrend) {
                if (row.length >= 4) {
                    Map<String, Object> hourData = new HashMap<>();
                    hourData.put("hour", getIntegerFromRow(row, 0));
                    hourData.put("avgGpuUtilization", getDoubleFromRow(row, 1));
                    hourData.put("avgMemoryUtilization", getDoubleFromRow(row, 2));
                    hourData.put("avgTemperature", getDoubleFromRow(row, 3));
                    
                    trendData.add(hourData);
                }
            }
        } catch (Exception e) {
            log.warn("Error converting hourly trend: {}", e.getMessage());
        }
        
        return trendData;
    }

    private List<Map<String, Object>> convertOverheatingMetrics(List<GpuUsageMetrics> overheatingMetrics) {
        List<Map<String, Object>> alerts = new ArrayList<>();
        
        try {
            for (GpuUsageMetrics metric : overheatingMetrics) {
                Map<String, Object> alert = new HashMap<>();
                alert.put("deviceId", metric.getDevice() != null ? metric.getDevice().getDeviceId() : "unknown");
                alert.put("migId", metric.getMigInstance() != null ? metric.getMigInstance().getMigId() : null);
                alert.put("temperature", metric.getTemperatureC());
                alert.put("timestamp", metric.getTimestamp());
                alert.put("severity", getTemperatureSeverity(metric.getTemperatureC()));
                
                alerts.add(alert);
            }
        } catch (Exception e) {
            log.warn("Error converting overheating metrics: {}", e.getMessage());
        }
        
        return alerts;
    }

    // 유틸리티 메서드들

    private String getTemperatureSeverity(Double temperature) {
        if (temperature == null) return "UNKNOWN";
        if (temperature >= 90) return "CRITICAL";
        if (temperature >= 85) return "WARNING";
        return "NORMAL";
    }

    private Map<String, Object> createEmptyStatistics(int hours) {
        return Map.of(
            "timeRange", hours + " hours",
            "deviceStatistics", new HashMap<>(),
            "modelStatistics", new HashMap<>(),
            "nodeStatistics", new HashMap<>(),
            "hourlyTrend", new ArrayList<>(),
            "lastUpdated", LocalDateTime.now(),
            "error", "Failed to retrieve statistics"
        );
    }

    // 안전한 타입 변환 메서드들
    
    private Double getDoubleFromRow(Object[] row, int index) {
        try {
            Object value = row[index];
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            return 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private Integer getIntegerFromRow(Object[] row, int index) {
        try {
            Object value = row[index];
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
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