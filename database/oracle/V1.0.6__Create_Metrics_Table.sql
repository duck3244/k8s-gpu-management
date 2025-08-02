-- ============================================================================
-- GPU Usage Metrics Table
-- Version: 1.0.6
-- Description: Create GPU usage metrics table for monitoring
-- ============================================================================

CREATE TABLE gpu_usage_metrics (
    metric_id VARCHAR2(50) PRIMARY KEY,
    device_id VARCHAR2(50),
    mig_id VARCHAR2(50),
    allocation_id VARCHAR2(50),
    gpu_utilization_pct NUMBER(5,2),
    memory_used_mb NUMBER(8),
    memory_total_mb NUMBER(8),
    memory_utilization_pct NUMBER(5,2),
    temperature_c NUMBER(5,2),
    power_draw_w NUMBER(6,2),
    fan_speed_pct NUMBER(5,2),
    clock_graphics_mhz NUMBER(5),
    clock_memory_mhz NUMBER(5),
    pcie_tx_mbps NUMBER(8,2),
    pcie_rx_mbps NUMBER(8,2),
    processes_count NUMBER(3),
    timestamp DATE DEFAULT SYSDATE,
    collection_source VARCHAR2(20) DEFAULT 'nvidia-smi',
    CONSTRAINT fk_gpu_metrics_device FOREIGN KEY (device_id) REFERENCES gpu_devices(device_id),
    CONSTRAINT fk_gpu_metrics_mig FOREIGN KEY (mig_id) REFERENCES mig_instances(mig_id),
    CONSTRAINT fk_gpu_metrics_allocation FOREIGN KEY (allocation_id) REFERENCES gpu_allocations(allocation_id)
);

-- 코멘트 추가
COMMENT ON TABLE gpu_usage_metrics IS 'GPU 사용량 메트릭 수집 테이블';
COMMENT ON COLUMN gpu_usage_metrics.collection_source IS '메트릭 수집 소스 (nvidia-smi, nvml 등)';
COMMENT ON COLUMN gpu_usage_metrics.timestamp IS '메트릭 수집 시간';

-- 파티션 설정 (대용량 데이터 처리용)
-- ALTER TABLE gpu_usage_metrics PARTITION BY RANGE (timestamp) 
-- INTERVAL(NUMTOYMINTERVAL(1, 'MONTH'));