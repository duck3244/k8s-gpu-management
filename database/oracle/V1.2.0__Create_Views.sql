-- ============================================================================
-- Create Views for GPU Management System
-- Version: 1.2.0
-- Description: Create analytical views for reporting and monitoring
-- ============================================================================

-- 클러스터 GPU 인벤토리 뷰
CREATE OR REPLACE VIEW v_cluster_gpu_inventory AS
SELECT 
    gm.model_name,
    gm.architecture,
    gm.memory_gb,
    gm.market_segment,
    gm.mig_support,
    COUNT(gd.device_id) as total_devices,
    SUM(CASE WHEN gd.device_status = 'ACTIVE' THEN 1 ELSE 0 END) as active_devices,
    SUM(CASE WHEN gd.device_status = 'MIG_ENABLED' THEN 1 ELSE 0 END) as mig_enabled_devices,
    SUM(gm.memory_gb * CASE WHEN gd.device_status IN ('ACTIVE', 'MIG_ENABLED') THEN 1 ELSE 0 END) as total_memory_gb,
    AVG(gd.current_temp_c) as avg_temperature,
    AVG(gd.current_power_w) as avg_power_consumption
FROM gpu_models gm
LEFT JOIN gpu_devices gd ON gm.model_id = gd.model_id
GROUP BY gm.model_name, gm.architecture, gm.memory_gb, gm.market_segment, gm.mig_support
ORDER BY COUNT(gd.device_id) DESC;

-- GPU 사용률 뷰
CREATE OR REPLACE VIEW v_gpu_utilization AS
SELECT 
    gn.node_name,
    gd.device_id,
    gm.model_name,
    gd.device_status,
    ga.allocation_id,
    ga.namespace,
    ga.pod_name,
    ga.status as allocation_status,
    ga.allocation_time,
    CASE 
        WHEN ga.allocation_id IS NOT NULL AND ga.status = 'ALLOCATED' THEN 'ALLOCATED'
        WHEN gd.device_status = 'MIG_ENABLED' THEN 'MIG_PARTITIONED'
        ELSE 'AVAILABLE'
    END as current_status
FROM gpu_devices gd
JOIN gpu_nodes gn ON gd.node_id = gn.node_id
JOIN gpu_models gm ON gd.model_id = gm.model_id
LEFT JOIN gpu_allocations ga ON gd.device_id = ga.allocated_resource 
    AND ga.status = 'ALLOCATED' AND ga.resource_type = 'FULL_GPU'
ORDER BY gn.node_name, gd.device_index;

-- MIG 인스턴스 상태 뷰
CREATE OR REPLACE VIEW v_mig_instance_status AS
SELECT 
    gn.node_name,
    gd.device_id,
    gm.model_name,
    mi.mig_id,
    mp.profile_name,
    mp.memory_gb as mig_memory_gb,
    mi.allocated,
    ga.allocation_id,
    ga.namespace,
    ga.pod_name,
    ga.allocation_time
FROM mig_instances mi
JOIN gpu_devices gd ON mi.device_id = gd.device_id
JOIN gpu_nodes gn ON gd.node_id = gn.node_id
JOIN gpu_models gm ON gd.model_id = gm.model_id
JOIN mig_profiles mp ON mi.profile_id = mp.profile_id
LEFT JOIN gpu_allocations ga ON mi.mig_id = ga.allocated_resource 
    AND ga.status = 'ALLOCATED' AND ga.resource_type = 'MIG_INSTANCE'
ORDER BY gn.node_name, gd.device_index, mi.instance_id;

-- 일별 GPU 사용 통계 뷰
CREATE OR REPLACE VIEW v_daily_gpu_usage_stats AS
SELECT 
    TO_CHAR(gum.timestamp, 'YYYY-MM-DD') as usage_date,
    gm.model_name,
    gm.architecture,
    COUNT(DISTINCT gum.device_id) as monitored_devices,
    ROUND(AVG(gum.gpu_utilization_pct), 2) as avg_gpu_utilization,
    ROUND(AVG(gum.memory_utilization_pct), 2) as avg_memory_utilization,
    ROUND(AVG(gum.temperature_c), 2) as avg_temperature,
    ROUND(AVG(gum.power_draw_w), 2) as avg_power_consumption,
    MAX(gum.temperature_c) as max_temperature,
    MAX(gum.power_draw_w) as max_power_consumption
FROM gpu_usage_metrics gum
JOIN gpu_devices gd ON gum.device_id = gd.device_id
JOIN gpu_models gm ON gd.model_id = gm.model_id
WHERE gum.timestamp >= TRUNC(SYSDATE) - 30
GROUP BY TO_CHAR(gum.timestamp, 'YYYY-MM-DD'), gm.model_name, gm.architecture
ORDER BY usage_date DESC, gm.model_name;

-- 비용 분석 뷰
CREATE OR REPLACE VIEW v_gpu_cost_analysis AS
SELECT 
    ga.namespace,
    ga.team_id,
    ga.project_id,
    gm.model_name,
    ga.resource_type,
    COUNT(ga.allocation_id) as total_allocations,
    SUM(CASE WHEN ga.status = 'ALLOCATED' THEN 1 ELSE 0 END) as active_allocations,
    SUM(
        CASE 
            WHEN ga.release_time IS NOT NULL 
            THEN (ga.release_time - ga.allocation_time) * 24 * NVL(ga.cost_per_hour, 0)
            WHEN ga.status = 'ALLOCATED'
            THEN (SYSDATE - ga.allocation_time) * 24 * NVL(ga.cost_per_hour, 0)
            ELSE NVL(ga.total_cost, 0)
        END
    ) as total_cost,
    SUM(
        CASE 
            WHEN ga.release_time IS NOT NULL 
            THEN (ga.release_time - ga.allocation_time) * 24
            WHEN ga.status = 'ALLOCATED'
            THEN (SYSDATE - ga.allocation_time) * 24
            ELSE 0
        END
    ) as total_usage_hours
FROM gpu_allocations ga
JOIN gpu_devices gd ON (
    (ga.resource_type = 'FULL_GPU' AND ga.allocated_resource = gd.device_id) OR
    (ga.resource_type = 'MIG_INSTANCE' AND ga.allocated_resource IN (
        SELECT mi.mig_id FROM mig_instances mi WHERE mi.device_id = gd.device_id
    ))
)
JOIN gpu_models gm ON gd.model_id = gm.model_id
WHERE ga.allocation_time >= TRUNC(SYSDATE) - 30
GROUP BY ga.namespace, ga.team_id, ga.project_id, gm.model_name, ga.resource_type
ORDER BY total_cost DESC;

-- 코멘트 추가
COMMENT ON VIEW v_cluster_gpu_inventory IS '클러스터 GPU 인벤토리 요약 뷰';
COMMENT ON VIEW v_gpu_utilization IS 'GPU 사용률 및 할당 상태 뷰';
COMMENT ON VIEW v_mig_instance_status IS 'MIG 인스턴스 상태 및 할당 정보 뷰';
COMMENT ON VIEW v_daily_gpu_usage_stats IS '일별 GPU 사용 통계 뷰';
COMMENT ON VIEW v_gpu_cost_analysis IS 'GPU 사용 비용 분석 뷰';