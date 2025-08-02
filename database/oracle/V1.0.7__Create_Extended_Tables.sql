-- ============================================================================
-- Extended Tables for GPU Management
-- Version: 1.0.7
-- Description: Create compatibility, benchmark, workload, and alert tables
-- ============================================================================

-- GPU 호환성 매트릭스 테이블
CREATE TABLE gpu_compatibility (
    compat_id VARCHAR2(50) PRIMARY KEY,
    model_id VARCHAR2(20) NOT NULL,
    cuda_version VARCHAR2(10) NOT NULL,
    driver_min_version VARCHAR2(20) NOT NULL,
    driver_max_version VARCHAR2(20),
    kubernetes_version VARCHAR2(20),
    container_runtime VARCHAR2(20) CHECK (container_runtime IN ('docker', 'containerd', 'cri-o')),
    os_type VARCHAR2(20) DEFAULT 'Linux',
    os_version VARCHAR2(50),
    supported CHAR(1) DEFAULT 'Y' CHECK (supported IN ('Y', 'N')),
    performance_impact_pct NUMBER(5,2) DEFAULT 0,
    known_issues CLOB,
    workarounds CLOB,
    tested_date DATE,
    notes VARCHAR2(500),
    created_date DATE DEFAULT SYSDATE,
    CONSTRAINT fk_gpu_compat_model FOREIGN KEY (model_id) REFERENCES gpu_models(model_id)
);

-- GPU 벤치마크 테이블
CREATE TABLE gpu_benchmarks (
    benchmark_id VARCHAR2(50) PRIMARY KEY,
    model_id VARCHAR2(20) NOT NULL,
    benchmark_type VARCHAR2(30) NOT NULL, -- FP32, FP16, INT8, Training, Inference, Gaming
    benchmark_name VARCHAR2(50), -- ResNet-50, BERT, 3DMark, etc.
    score NUMBER(12,2) NOT NULL,
    score_unit VARCHAR2(20), -- FPS, TOPS, Images/sec, Tokens/sec
    batch_size NUMBER(4),
    precision VARCHAR2(10), -- FP32, FP16, INT8, Mixed
    framework VARCHAR2(20), -- TensorFlow, PyTorch, CUDA
    test_duration_minutes NUMBER(6,2),
    test_date DATE NOT NULL,
    test_environment VARCHAR2(200),
    hardware_config CLOB, -- JSON format
    software_config CLOB, -- JSON format
    notes VARCHAR2(500),
    created_date DATE DEFAULT SYSDATE,
    CONSTRAINT fk_gpu_benchmark_model FOREIGN KEY (model_id) REFERENCES gpu_models(model_id)
);

-- 워크로드 프로필 테이블
CREATE TABLE workload_profiles (
    profile_id VARCHAR2(50) PRIMARY KEY,
    workload_name VARCHAR2(100) NOT NULL,
    workload_type VARCHAR2(30) NOT NULL 
        CHECK (workload_type IN ('Training', 'Inference', 'Development', 'Gaming', 'Rendering', 'Mining')),
    min_memory_gb NUMBER(3) NOT NULL,
    preferred_memory_gb NUMBER(3),
    min_compute_capability VARCHAR2(10),
    preferred_architectures VARCHAR2(200), -- Comma separated: Ampere,Hopper
    requires_mig CHAR(1) DEFAULT 'N' CHECK (requires_mig IN ('Y', 'N')),
    max_sharing_ratio NUMBER(2) DEFAULT 1, -- 1 for exclusive, >1 for shared
    performance_requirements CLOB, -- JSON format
    resource_constraints CLOB, -- JSON format
    cost_sensitivity VARCHAR2(10) DEFAULT 'MEDIUM' 
        CHECK (cost_sensitivity IN ('LOW', 'MEDIUM', 'HIGH')),
    sla_requirements CLOB, -- JSON format
    description VARCHAR2(500),
    created_by VARCHAR2(50),
    created_date DATE DEFAULT SYSDATE,
    updated_date DATE DEFAULT SYSDATE
);

-- 알림 규칙 테이블
CREATE TABLE alert_rules (
    rule_id VARCHAR2(50) PRIMARY KEY,
    rule_name VARCHAR2(100) NOT NULL,
    rule_type VARCHAR2(30) NOT NULL, -- TEMPERATURE, UTILIZATION, MEMORY, POWER, FAILURE
    target_type VARCHAR2(20) CHECK (target_type IN ('NODE', 'DEVICE', 'MIG', 'ALLOCATION')),
    target_filter VARCHAR2(200), -- JSON filter criteria
    threshold_value NUMBER(10,2),
    threshold_operator VARCHAR2(10) CHECK (threshold_operator IN ('>', '<', '>=', '<=', '=', '!=')),
    severity VARCHAR2(10) DEFAULT 'MEDIUM' CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    evaluation_interval_minutes NUMBER(4) DEFAULT 5,
    notification_channels VARCHAR2(200), -- email,slack,webhook
    enabled CHAR(1) DEFAULT 'Y' CHECK (enabled IN ('Y', 'N')),
    created_date DATE DEFAULT SYSDATE,
    updated_date DATE DEFAULT SYSDATE
);

-- 시스템 알림 테이블
CREATE TABLE system_alerts (
    alert_id VARCHAR2(50) PRIMARY KEY,
    rule_id VARCHAR2(50),
    alert_type VARCHAR2(30) NOT NULL,
    severity VARCHAR2(10) NOT NULL,
    target_type VARCHAR2(20),
    target_id VARCHAR2(50),
    message VARCHAR2(500) NOT NULL,
    details CLOB,
    metric_value NUMBER(10,2),
    threshold_value NUMBER(10,2),
    status VARCHAR2(20) DEFAULT 'ACTIVE' 
        CHECK (status IN ('ACTIVE', 'ACKNOWLEDGED', 'RESOLVED', 'SUPPRESSED')),
    created_date DATE DEFAULT SYSDATE,
    acknowledged_date DATE,
    acknowledged_by VARCHAR2(50),
    resolved_date DATE,
    resolved_by VARCHAR2(50),
    CONSTRAINT fk_system_alerts_rule FOREIGN KEY (rule_id) REFERENCES alert_rules(rule_id)
);

-- 코멘트 추가
COMMENT ON TABLE gpu_compatibility IS 'GPU 호환성 매트릭스 테이블';
COMMENT ON TABLE gpu_benchmarks IS 'GPU 성능 벤치마크 결과 테이블';
COMMENT ON TABLE workload_profiles IS '워크로드 프로필 정의 테이블';
COMMENT ON TABLE alert_rules IS '알림 규칙 설정 테이블';
COMMENT ON TABLE system_alerts IS '시스템 알림 관리 테이블';