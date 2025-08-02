-- ============================================================================
-- Create Stored Procedures for GPU Management System
-- Version: 1.3.0
-- Description: Create stored procedures for automation and optimization
-- ============================================================================

-- GPU 자원 최적화 프로시저
CREATE OR REPLACE PROCEDURE p_optimize_gpu_allocation AS
    v_optimized_count NUMBER := 0;
    v_released_count NUMBER := 0;
BEGIN
    -- 만료된 할당 정리
    UPDATE gpu_allocations 
    SET status = 'EXPIRED', 
        release_time = SYSDATE,
        updated_date = SYSDATE
    WHERE status = 'ALLOCATED' 
    AND planned_release_time < SYSDATE;
    
    v_released_count := SQL%ROWCOUNT;
    
    -- 미사용 MIG 인스턴스 해제
    UPDATE mig_instances 
    SET allocated = 'N', 
        last_used_date = SYSDATE
    WHERE allocated = 'Y' 
    AND mig_id NOT IN (
        SELECT allocated_resource 
        FROM gpu_allocations 
        WHERE status = 'ALLOCATED' AND resource_type = 'MIG_INSTANCE'
    );
    
    -- 노드별 GPU 가용성 업데이트
    UPDATE gpu_nodes n
    SET available_gpus = (
        SELECT COUNT(*)
        FROM gpu_devices d
        WHERE d.node_id = n.node_id 
        AND d.device_status = 'ACTIVE'
        AND d.device_id NOT IN (
            SELECT DISTINCT allocated_resource 
            FROM gpu_allocations 
            WHERE status = 'ALLOCATED' AND resource_type = 'FULL_GPU'
        )
    ),
    updated_date = SYSDATE;
    
    v_optimized_count := SQL%ROWCOUNT;
    
    COMMIT;
    
    DBMS_OUTPUT.PUT_LINE('GPU 자원 최적화 완료');
    DBMS_OUTPUT.PUT_LINE('- 만료된 할당 해제: ' || v_released_count || '건');
    DBMS_OUTPUT.PUT_LINE('- 노드 가용성 업데이트: ' || v_optimized_count || '건');
    
EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        DBMS_OUTPUT.PUT_LINE('오류 발생: ' || SQLERRM);
        RAISE;
END p_optimize_gpu_allocation;
/

-- MIG 인스턴스 생성 프로시저
CREATE OR REPLACE PROCEDURE p_create_mig_instances(
    p_device_id IN VARCHAR2,
    p_profile_ids IN VARCHAR2  -- 콤마로 구분된 프로필 ID
) AS
    v_profile_id VARCHAR2(20);
    v_instance_count NUMBER;
    v_max_instances NUMBER;
    v_pos NUMBER := 1;
    v_next_pos NUMBER;
    v_instance_id NUMBER := 0;
BEGIN
    -- 기존 MIG 인스턴스 삭제
    DELETE FROM mig_instances WHERE device_id = p_device_id;
    
    -- GPU를 MIG 모드로 설정
    UPDATE gpu_devices 
    SET device_status = 'MIG_ENABLED', updated_date = SYSDATE 
    WHERE device_id = p_device_id;
    
    -- 콤마로 구분된 프로필 ID 처리
    WHILE v_pos <= LENGTH(p_profile_ids) LOOP
        v_next_pos := INSTR(p_profile_ids, ',', v_pos);
        IF v_next_pos = 0 THEN
            v_next_pos := LENGTH(p_profile_ids) + 1;
        END IF;
        
        v_profile_id := TRIM(SUBSTR(p_profile_ids, v_pos, v_next_pos - v_pos));
        
        -- 프로필의 최대 인스턴스 수 확인
        SELECT max_instances_per_gpu INTO v_max_instances
        FROM mig_profiles 
        WHERE profile_id = v_profile_id;
        
        -- MIG 인스턴스 생성
        FOR i IN 1..v_max_instances LOOP
            INSERT INTO mig_instances (
                mig_id, device_id, profile_id, instance_id, 
                mig_uuid, allocated, created_date
            ) VALUES (
                p_device_id || '_MIG_' || v_instance_id,
                p_device_id,
                v_profile_id,
                v_instance_id,
                'MIG-' || SYS_GUID(),
                'N',
                SYSDATE
            );
            
            v_instance_id := v_instance_id + 1;
        END LOOP;
        
        v_pos := v_next_pos + 1;
    END LOOP;
    
    COMMIT;
    
    DBMS_OUTPUT.PUT_LINE('MIG 인스턴스 생성 완료: ' || p_device_id);
    
EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        DBMS_OUTPUT.PUT_LINE('MIG 인스턴스 생성 실패: ' || SQLERRM);
        RAISE;
END p_create_mig_instances;
/

-- GPU 메트릭 수집 프로시저 (샘플 데이터 생성용)
CREATE OR REPLACE PROCEDURE p_collect_gpu_metrics AS
    CURSOR c_devices IS
        SELECT device_id, gpu_uuid, model_id
        FROM gpu_devices 
        WHERE device_status IN ('ACTIVE', 'MIG_ENABLED');
        
    v_metric_id VARCHAR2(50);
    v_count NUMBER := 0;
BEGIN
    FOR rec IN c_devices LOOP
        v_metric_id := 'METRIC_' || TO_CHAR(SYSDATE, 'YYYYMMDDHH24MISS') || '_' || rec.device_id;
        
        -- 실제 환경에서는 nvidia-smi나 다른 모니터링 도구에서 데이터를 가져옴
        -- 여기서는 샘플 데이터 생성
        INSERT INTO gpu_usage_metrics (
            metric_id, device_id, gpu_utilization_pct, memory_used_mb, 
            memory_total_mb, memory_utilization_pct, temperature_c, 
            power_draw_w, fan_speed_pct, timestamp
        ) VALUES (
            v_metric_id,
            rec.device_id,
            ROUND(DBMS_RANDOM.VALUE(10, 95), 2),  -- 10-95% GPU 사용률
            ROUND(DBMS_RANDOM.VALUE(1000, 8000)), -- 메모리 사용량 (MB)
            8192, -- 총 메모리 (예시)
            ROUND(DBMS_RANDOM.VALUE(20, 90), 2),  -- 메모리 사용률
            ROUND(DBMS_RANDOM.VALUE(40, 85), 2),  -- 온도
            ROUND(DBMS_RANDOM.VALUE(100, 300), 2), -- 전력 소모
            ROUND(DBMS_RANDOM.VALUE(30, 80), 2),  -- 팬 속도
            SYSDATE
        );
        
        v_count := v_count + 1;
    END LOOP;
    
    COMMIT;
    
    DBMS_OUTPUT.PUT_LINE('GPU 메트릭 수집 완료: ' || v_count || '개 장비');
    
EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        DBMS_OUTPUT.PUT_LINE('메트릭 수집 실패: ' || SQLERRM);
        RAISE;
END p_collect_gpu_metrics;
/

-- 오래된 메트릭 데이터 정리 프로시저
CREATE OR REPLACE PROCEDURE p_cleanup_old_metrics(
    p_retention_days IN NUMBER DEFAULT 30
) AS
    v_cutoff_date DATE;
    v_deleted_count NUMBER;
BEGIN
    v_cutoff_date := SYSDATE - p_retention_days;
    
    DELETE FROM gpu_usage_metrics 
    WHERE timestamp < v_cutoff_date;
    
    v_deleted_count := SQL%ROWCOUNT;
    
    COMMIT;
    
    DBMS_OUTPUT.PUT_LINE('메트릭 정리 완료: ' || v_deleted_count || '건 삭제');
    DBMS_OUTPUT.PUT_LINE('보관 기간: ' || p_retention_days || '일');
    
EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        DBMS_OUTPUT.PUT_LINE('메트릭 정리 실패: ' || SQLERRM);
        RAISE;
END p_cleanup_old_metrics;
/