-- ============================================================================
-- Create Triggers for GPU Management System
-- Version: 1.4.0
-- Description: Create triggers for automatic data management
-- ============================================================================

-- GPU 장비 업데이트 트리거
CREATE OR REPLACE TRIGGER tr_gpu_devices_update
    BEFORE UPDATE ON gpu_devices
    FOR EACH ROW
BEGIN
    :NEW.updated_date := SYSDATE;
END tr_gpu_devices_update;
/

-- GPU 할당 업데이트 트리거
CREATE OR REPLACE TRIGGER tr_gpu_allocations_update
    BEFORE UPDATE ON gpu_allocations
    FOR EACH ROW
BEGIN
    :NEW.updated_date := SYSDATE;
    
    -- 할당 해제 시 총 비용 계산
    IF :NEW.status = 'RELEASED' AND :OLD.status = 'ALLOCATED' THEN
        :NEW.release_time := SYSDATE;
        IF :NEW.cost_per_hour IS NOT NULL THEN
            :NEW.total_cost := (:NEW.release_time - :NEW.allocation_time) * 24 * :NEW.cost_per_hour;
        END IF;
    END IF;
END tr_gpu_allocations_update;
/

-- GPU 노드 업데이트 트리거
CREATE OR REPLACE TRIGGER tr_gpu_nodes_update
    BEFORE UPDATE ON gpu_nodes
    FOR EACH ROW
BEGIN
    :NEW.updated_date := SYSDATE;
END tr_gpu_nodes_update;
/

-- 워크로드 프로필 업데이트 트리거
CREATE OR REPLACE TRIGGER tr_workload_profiles_update
    BEFORE UPDATE ON workload_profiles
    FOR EACH ROW
BEGIN
    :NEW.updated_date := SYSDATE;
END tr_workload_profiles_update;
/

-- 알림 규칙 업데이트 트리거
CREATE OR REPLACE TRIGGER tr_alert_rules_update
    BEFORE UPDATE ON alert_rules
    FOR EACH ROW
BEGIN
    :NEW.updated_date := SYSDATE;
END tr_alert_rules_update;
/

-- MIG 인스턴스 할당 상태 변경 트리거
CREATE OR REPLACE TRIGGER tr_mig_instances_allocation
    AFTER UPDATE OF allocated ON mig_instances
    FOR EACH ROW
BEGIN
    -- MIG 인스턴스가 할당될 때
    IF :NEW.allocated = 'Y' AND :OLD.allocated = 'N' THEN
        :NEW.allocated_date := SYSDATE;
    END IF;
    
    -- MIG 인스턴스가 해제될 때
    IF :NEW.allocated = 'N' AND :OLD.allocated = 'Y' THEN
        :NEW.last_used_date := SYSDATE;
    END IF;
END tr_mig_instances_allocation;
/

-- 알림 생성 트리거 (GPU 온도 기준)
CREATE OR REPLACE TRIGGER tr_gpu_temperature_alert
    AFTER INSERT OR UPDATE OF current_temp_c ON gpu_devices
    FOR EACH ROW
DECLARE
    v_alert_id VARCHAR2(50);
    v_threshold NUMBER := 85.0;
BEGIN
    -- 온도가 임계값을 초과하는 경우 알림 생성
    IF :NEW.current_temp_c IS NOT NULL AND :NEW.current_temp_c > v_threshold THEN
        v_alert_id := 'ALERT_' || TO_CHAR(SYSDATE, 'YYYYMMDDHH24MISS') || '_' || :NEW.device_id;
        
        INSERT INTO system_alerts (
            alert_id, alert_type, severity, target_type, target_id,
            message, metric_value, threshold_value, status, created_date
        ) VALUES (
            v_alert_id,
            'TEMPERATURE',
            CASE WHEN :NEW.current_temp_c > 90 THEN 'CRITICAL' ELSE 'HIGH' END,
            'DEVICE',
            :NEW.device_id,
            'GPU temperature exceeded threshold: ' || :NEW.current_temp_c || '°C',
            :NEW.current_temp_c,
            v_threshold,
            'ACTIVE',
            SYSDATE
        );
    END IF;
END tr_gpu_temperature_alert;
/