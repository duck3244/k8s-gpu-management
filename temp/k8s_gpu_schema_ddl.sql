-- ============================================================================
-- K8s GPU Management System - Oracle Database Schema
-- ============================================================================
-- 작성일: 2025-08-01
-- 목적: Kubernetes 환경의 다양한 GPU 리소스 관리
-- 지원 GPU: GTX 1080, Titan XP, RTX 시리즈, A100, H100 등 14종
-- ============================================================================

-- 기존 테이블 삭제 (순서 중요 - 외래키 관계 고려)
DROP TABLE gpu_usage_metrics CASCADE CONSTRAINTS;
DROP TABLE gpu_benchmarks CASCADE CONSTRAINTS;
DROP TABLE gpu_compatibility CASCADE CONSTRAINTS;
DROP TABLE gpu_allocations CASCADE CONSTRAINTS;
DROP TABLE mig_instances CASCADE CONSTRAINTS;
DROP TABLE mig_profiles CASCADE CONSTRAINTS;
DROP TABLE gpu_devices CASCADE CONSTRAINTS;
DROP TABLE gpu_nodes CASCADE CONSTRAINTS;
DROP TABLE gpu_models CASCADE CONSTRAINTS;
DROP TABLE workload_profiles CASCADE CONSTRAINTS;
DROP TABLE alert_rules CASCADE CONSTRAINTS;
DROP TABLE system_alerts CASCADE CONSTRAINTS;

-- 시퀀스 삭제
DROP SEQUENCE seq_gpu_allocation_id;
DROP SEQUENCE seq_metric_id;
DROP SEQUENCE seq_benchmark_id;
DROP SEQUENCE seq_alert_id;

-- ============================================================================
-- 1. GPU 모델 마스터 테이블
-- ============================================================================
CREATE TABLE gpu_models (
    model_id VARCHAR2(20) PRIMARY KEY,
    model_name VARCHAR2(50) NOT NULL,
    manufacturer VARCHAR2(20) DEFAULT 'NVIDIA' NOT NULL,
    architecture VARCHAR2(30) NOT NULL, -- Pascal, Turing, Ampere, Hopper, Ada Lovelace
    memory_gb NUMBER(3) NOT NULL,
    cuda_cores NUMBER(6),
    tensor_cores NUMBER(4),
    rt_cores NUMBER(4) DEFAULT 0,
    base_clock_mhz NUMBER(5),
    boost_clock_mhz NUMBER(5),
    memory_bandwidth_gbps NUMBER(6,1),
    memory_type VARCHAR2(20), -- GDDR6, GDDR6X, HBM2, HBM3
    power_consumption_w NUMBER(4) NOT NULL,
    pcie_generation VARCHAR2(10),
    mig_support CHAR(1) DEFAULT 'N' CHECK (mig_support IN ('Y', 'N')),
    max_mig_instances NUMBER(2) DEFAULT 0,
    compute_capability VARCHAR2(10), -- 6.1, 7.5, 8.0, 9.0
    release_year NUMBER(4),
    market_segment VARCHAR2(20) CHECK (market_segment IN ('Gaming', 'Professional', 'Datacenter')),
    end_of_life_date DATE,
    created_date DATE DEFAULT SYSDATE,
    updated_date DATE DEFAULT SYSDATE
);

-- GPU 모델 인덱스
CREATE INDEX idx_gpu_models_arch ON gpu_models(architecture);
CREATE INDEX idx_gpu_models_segment ON gpu_models(market_segment);
CREATE INDEX idx_gpu_models_mig ON gpu_models(mig_support);

-- ============================================================================
-- 2. Kubernetes 노드 테이블
-- ============================================================================
CREATE TABLE gpu_nodes (
    node_id VARCHAR2(50) PRIMARY KEY,
    node_name VARCHAR2(100) NOT NULL,
    cluster_name VARCHAR2(50),
    node_ip VARCHAR2(15),
    total_gpus NUMBER(3) DEFAULT 0,
    available_gpus NUMBER(3) DEFAULT 0,
    node_status VARCHAR2(20) DEFAULT 'ACTIVE' CHECK (node_status IN ('ACTIVE', 'INACTIVE', 'MAINTENANCE', 'FAILED')),
    kubernetes_version VARCHAR2(20),
    docker_version VARCHAR2(20),
    nvidia_driver_version VARCHAR2(20),
    node_labels CLOB, -- JSON format for node labels
    taints CLOB, -- JSON format for node taints
    created_date DATE DEFAULT SYSDATE,
    updated_date DATE DEFAULT SYSDATE
);

-- 노드 인덱스
CREATE INDEX idx_gpu_nodes_status ON gpu_nodes(node_status);
CREATE INDEX idx_gpu_nodes_cluster ON gpu_nodes(cluster_name);

-- ============================================================================
-- 3. GPU 장비 테이블
-- ============================================================================
CREATE TABLE gpu_devices (
    device_id VARCHAR2(50) PRIMARY KEY,
    node_id VARCHAR2(50) NOT NULL,
    model_id VARCHAR2(20) NOT NULL,
    device_index NUMBER(2) NOT NULL, -- GPU index on node (0, 1, 2, ...)
    serial_number VARCHAR2(50),
    pci_address VARCHAR2(20) NOT NULL,
    gpu_uuid VARCHAR2(100) UNIQUE NOT NULL,
    device_status VARCHAR2(20) DEFAULT 'ACTIVE' CHECK (device_status IN ('ACTIVE', 'INACTIVE', 'MAINTENANCE', 'FAILED', 'MIG_ENABLED')),
    current_temp_c NUMBER(5,2),
    max_temp_c NUMBER(5,2) DEFAULT 83,
    current_power_w NUMBER(6,2),
    max_power_w NUMBER(6,2),
    driver_version VARCHAR2(20),
    firmware_version VARCHAR2(20),
    vbios_version VARCHAR2(20),
    installation_date DATE,
    last_maintenance_date DATE,
    warranty_expiry_date DATE,
    purchase_cost NUMBER(10,2),
    depreciation_months NUMBER(3) DEFAULT 36,
    created_date DATE DEFAULT SYSDATE,
    updated_date DATE DEFAULT SYSDATE,
    CONSTRAINT fk_gpu_devices_node FOREIGN KEY (node_id) REFERENCES gpu_nodes(node_id),
    CONSTRAINT fk_gpu_devices_model FOREIGN KEY (model_id) REFERENCES gpu_models(model_id),
    CONSTRAINT uk_gpu_devices_node_idx UNIQUE (node_id, device_index)
);

-- GPU 장비 인덱스
CREATE INDEX idx_gpu_devices_node ON gpu_devices(node_id);
CREATE INDEX idx_gpu_devices_model ON gpu_devices(model_id);
CREATE INDEX idx_gpu_devices_status ON gpu_devices(device_status);
CREATE INDEX idx_gpu_devices_uuid ON gpu_devices(gpu_uuid);

-- ============================================================================
-- 4. MIG 프로필 테이블 (H100, A100 전용)
-- ============================================================================
CREATE TABLE mig_profiles (
    profile_id VARCHAR2(20) PRIMARY KEY,
    model_id VARCHAR2(20) NOT NULL,
    profile_name VARCHAR2(50) NOT NULL, -- 1g.5gb, 2g.10gb, 3g.20gb, 4g.20gb, 7g.40gb
    compute_slices NUMBER(2) NOT NULL,
    memory_slices NUMBER(2) NOT NULL,
    memory_gb NUMBER(3) NOT NULL,
    max_instances_per_gpu NUMBER(2) NOT NULL,
    performance_ratio NUMBER(4,2), -- Relative performance compared to full GPU
    use_case VARCHAR2(100), -- Training, Inference, Development
    description VARCHAR2(200),
    created_date DATE DEFAULT SYSDATE,
    CONSTRAINT fk_mig_profiles_model FOREIGN KEY (model_id) REFERENCES gpu_models(model_id),
    CONSTRAINT uk_mig_profiles_model_name UNIQUE (model_id, profile_name)
);

-- MIG 프로필 인덱스
CREATE INDEX idx_mig_profiles_model ON mig_profiles(model_id);

-- ============================================================================
-- 5. MIG 인스턴스 테이블
-- ============================================================================
CREATE TABLE mig_instances (
    mig_id VARCHAR2(50) PRIMARY KEY,
    device_id VARCHAR2(50) NOT NULL,
    profile_id VARCHAR2(20) NOT NULL,
    instance_id NUMBER(2) NOT NULL, -- GPU 내에서 MIG 인스턴스 ID (0-6)
    mig_uuid VARCHAR2(100) UNIQUE NOT NULL,
    allocated CHAR(1) DEFAULT 'N' CHECK (allocated IN ('Y', 'N')),
    instance_status VARCHAR2(20) DEFAULT 'ACTIVE' CHECK (instance_status IN ('ACTIVE', 'INACTIVE', 'FAILED')),
    created_date DATE DEFAULT SYSDATE,
    allocated_date DATE,
    last_used_date DATE,
    CONSTRAINT fk_mig_instances_device FOREIGN KEY (device_id) REFERENCES gpu_devices(device_id),
    CONSTRAINT fk_mig_instances_profile FOREIGN KEY (profile_id) REFERENCES mig_profiles(profile_id),
    CONSTRAINT uk_mig_instances_device_id UNIQUE (device_id, instance_id)
);

-- MIG 인스턴스 인덱스
CREATE INDEX idx_mig_instances_device ON mig_instances(device_id);
CREATE INDEX idx_mig_instances_profile ON mig_instances(profile_id);
CREATE INDEX idx_mig_instances_allocated ON mig_instances(allocated);

-- ============================================================================
-- 6. GPU 할당 테이블
-- ============================================================================
CREATE TABLE gpu_allocations (
    allocation_id VARCHAR2(50) PRIMARY KEY,
    namespace VARCHAR2(50) NOT NULL,
    pod_name VARCHAR2(100) NOT NULL,
    container_name VARCHAR2(100),
    workload_type VARCHAR2(30), -- Training, Inference, Development, Gaming
    resource_type VARCHAR2(20) NOT NULL CHECK (resource_type IN ('FULL_GPU', 'MIG_INSTANCE', 'SHARED_GPU')),
    allocated_resource VARCHAR2(50) NOT NULL, -- device_id or mig_id
    requested_memory_gb NUMBER(3),
    allocated_memory_gb NUMBER(3),
    priority_class VARCHAR2(20) DEFAULT 'normal',
    allocation_time DATE DEFAULT SYSDATE,
    planned_release_time DATE,
    release_time DATE,
    status VARCHAR2(20) DEFAULT 'ALLOCATED' CHECK (status IN ('PENDING', 'ALLOCATED', 'RELEASED', 'FAILED', 'EXPIRED')),
    cost_per_hour NUMBER(8,4),
    total_cost NUMBER(10,2),
    user_id VARCHAR2(50),
    team_id VARCHAR2(50),
    project_id VARCHAR2(50),
    created_date DATE DEFAULT SYSDATE,
    updated_date DATE DEFAULT SYSDATE
);

-- 할당 인덱스
CREATE INDEX idx_gpu_allocations_namespace ON gpu_allocations(namespace);
CREATE INDEX idx_gpu_allocations_status ON gpu_allocations(status);
CREATE INDEX idx_gpu_allocations_resource ON gpu_allocations(allocated_resource);
CREATE INDEX idx_gpu_allocations_time ON gpu_allocations(allocation_time);
CREATE INDEX idx_gpu_allocations_user ON gpu_allocations(user_id);

-- ============================================================================
-- 7. GPU 사용량 메트릭 테이블
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

-- 메트릭 인덱스
CREATE INDEX idx_gpu_metrics_device_time ON gpu_usage_metrics(device_id, timestamp);
CREATE INDEX idx_gpu_metrics_mig_time ON gpu_usage_metrics(mig_id, timestamp);
CREATE INDEX idx_gpu_metrics_timestamp ON gpu_usage_metrics(timestamp);

-- 파티션 테이블로 변경 (월별 파티션)
-- ALTER TABLE gpu_usage_metrics PARTITION BY RANGE (timestamp) INTERVAL(NUMTOYMINTERVAL(1, 'MONTH'));

-- ============================================================================
-- 8. GPU 호환성 매트릭스 테이블
-- ============================================================================
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

-- 호환성 인덱스
CREATE INDEX idx_gpu_compat_model ON gpu_compatibility(model_id);
CREATE INDEX idx_gpu_compat_cuda ON gpu_compatibility(cuda_version);

-- ============================================================================
-- 9. GPU 벤치마크 테이블
-- ============================================================================
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

-- 벤치마크 인덱스
CREATE INDEX idx_gpu_benchmark_model ON gpu_benchmarks(model_id);
CREATE INDEX idx_gpu_benchmark_type ON gpu_benchmarks(benchmark_type);
CREATE INDEX idx_gpu_benchmark_date ON gpu_benchmarks(test_date);

-- ============================================================================
-- 10. 워크로드 프로필 테이블
-- ============================================================================
CREATE TABLE workload_profiles (
    profile_id VARCHAR2(50) PRIMARY KEY,
    workload_name VARCHAR2(100) NOT NULL,
    workload_type VARCHAR2(30) NOT NULL CHECK (workload_type IN ('Training', 'Inference', 'Development', 'Gaming', 'Rendering', 'Mining')),
    min_memory_gb NUMBER(3) NOT NULL,
    preferred_memory_gb NUMBER(3),
    min_compute_capability VARCHAR2(10),
    preferred_architectures VARCHAR2(200), -- Comma separated: Ampere,Hopper
    requires_mig CHAR(1) DEFAULT 'N' CHECK (requires_mig IN ('Y', 'N')),
    max_sharing_ratio NUMBER(2) DEFAULT 1, -- 1 for exclusive, >1 for shared
    performance_requirements CLOB, -- JSON format
    resource_constraints CLOB, -- JSON format
    cost_sensitivity VARCHAR2(10) DEFAULT 'MEDIUM' CHECK (cost_sensitivity IN ('LOW', 'MEDIUM', 'HIGH')),
    sla_requirements CLOB, -- JSON format
    description VARCHAR2(500),
    created_by VARCHAR2(50),
    created_date DATE DEFAULT SYSDATE,
    updated_date DATE DEFAULT SYSDATE
);

-- 워크로드 프로필 인덱스
CREATE INDEX idx_workload_profiles_type ON workload_profiles(workload_type);
CREATE INDEX idx_workload_profiles_memory ON workload_profiles(min_memory_gb);

-- ============================================================================
-- 11. 알림 규칙 테이블
-- ============================================================================
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

-- ============================================================================
-- 12. 시스템 알림 테이블
-- ============================================================================
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
    status VARCHAR2(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ACKNOWLEDGED', 'RESOLVED', 'SUPPRESSED')),
    created_date DATE DEFAULT SYSDATE,
    acknowledged_date DATE,
    acknowledged_by VARCHAR2(50),
    resolved_date DATE,
    resolved_by VARCHAR2(50),
    CONSTRAINT fk_system_alerts_rule FOREIGN KEY (rule_id) REFERENCES alert_rules(rule_id)
);

-- 알림 인덱스
CREATE INDEX idx_system_alerts_status ON system_alerts(status);
CREATE INDEX idx_system_alerts_severity ON system_alerts(severity);
CREATE INDEX idx_system_alerts_date ON system_alerts(created_date);

-- ============================================================================
-- 시퀀스 생성
-- ============================================================================
CREATE SEQUENCE seq_gpu_allocation_id START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_metric_id START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_benchmark_id START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_alert_id START WITH 1 INCREMENT BY 1 NOCACHE;

-- ============================================================================
-- 샘플 데이터 삽입
-- ============================================================================

-- GPU 모델 데이터
INSERT INTO gpu_models VALUES ('GTX1080', 'GeForce GTX 1080', 'NVIDIA', 'Pascal', 8, 2560, 0, 0, 1607, 1733, 320.0, 'GDDR5X', 180, 'PCIe 3.0', 'N', 0, '6.1', 2016, 'Gaming', TO_DATE('2021-12-31', 'YYYY-MM-DD'), SYSDATE, SYSDATE);
INSERT INTO gpu_models VALUES ('GTX1080TI', 'GeForce GTX 1080 Ti', 'NVIDIA', 'Pascal', 11, 3584, 0, 0, 1480, 1582, 484.0, 'GDDR5X', 250, 'PCIe 3.0', 'N', 0, '6.1', 2017, 'Gaming', TO_DATE('2022-12-31', 'YYYY-MM-DD'), SYSDATE, SYSDATE);
INSERT INTO gpu_models VALUES ('TITANXP', 'Titan Xp', 'NVIDIA', 'Pascal', 12, 3840, 0, 0, 1417, 1531, 547.7, 'GDDR5X', 250, 'PCIe 3.0', 'N', 0, '6.1', 2017, 'Professional', TO_DATE('2022-12-31', 'YYYY-MM-DD'), SYSDATE, SYSDATE);
INSERT INTO gpu_models VALUES ('RTX2080', 'GeForce RTX 2080', 'NVIDIA', 'Turing', 8, 2944, 368, 46, 1515, 1710, 448.0, 'GDDR6', 215, 'PCIe 3.0', 'N', 0, '7.5', 2018, 'Gaming', NULL, SYSDATE, SYSDATE);
INSERT INTO gpu_models VALUES ('RTX2080TI', 'GeForce RTX 2080 Ti', 'NVIDIA', 'Turing', 11, 4352, 544, 68, 1350, 1545, 616.0, 'GDDR6', 250, 'PCIe 3.0', 'N', 0, '7.5', 2018, 'Gaming', NULL, SYSDATE, SYSDATE);
INSERT INTO gpu_models VALUES ('RTX3080', 'GeForce RTX 3080', 'NVIDIA', 'Ampere', 10, 8704, 272, 68, 1440, 1710, 760.3, 'GDDR6X', 320, 'PCIe 4.0', 'N', 0, '8.6', 2020, 'Gaming', NULL, SYSDATE, SYSDATE);
INSERT INTO gpu_models VALUES ('RTX3090', 'GeForce RTX 3090', 'NVIDIA', 'Ampere', 24, 10496, 328, 82, 1395, 1695, 936.2, 'GDDR6X', 350, 'PCIe 4.0', 'N', 0, '8.6', 2020, 'Gaming', NULL, SYSDATE, SYSDATE);
INSERT INTO gpu_models VALUES ('RTX4080', 'GeForce RTX 4080', 'NVIDIA', 'Ada Lovelace', 16, 9728, 304, 76, 1260, 2505, 716.8, 'GDDR6X', 320, 'PCIe 4.0', 'N', 0, '8.9', 2022, 'Gaming', NULL, SYSDATE, SYSDATE);
INSERT INTO gpu_models VALUES ('RTX4090', 'GeForce RTX 4090', 'NVIDIA', 'Ada Lovelace', 24, 16384, 512, 128, 1230, 2520, 1008.0, 'GDDR6X', 450, 'PCIe 4.0', 'N', 0, '8.9', 2022, 'Gaming', NULL, SYSDATE, SYSDATE);
INSERT INTO gpu_models VALUES ('V100_16GB', 'Tesla V100 PCIe 16GB', 'NVIDIA', 'Volta', 16, 5120, 640, 0, 1245, 1380, 900.0, 'HBM2', 250, 'PCIe 3.0', 'N', 0, '7.0', 2017, 'Datacenter', NULL, SYSDATE, SYSDATE);
INSERT INTO gpu_models VALUES ('V100_32GB', 'Tesla V100 SXM2 32GB', 'NVIDIA', 'Volta', 32, 5120, 640, 0, 1290, 1530, 900.0, 'HBM2', 300, 'SXM2', 'N', 0, '7.0', 2017, 'Datacenter', NULL, SYSDATE, SYSDATE);
INSERT INTO gpu_models VALUES ('A100_40GB', 'A100 PCIe 40GB', 'NVIDIA', 'Ampere', 40, 6912, 432, 0, 765, 1065, 1555.0, 'HBM2e', 250, 'PCIe 4.0', 'Y', 7, '8.0', 2020, 'Datacenter', NULL, SYSDATE, SYSDATE);
INSERT INTO gpu_models VALUES ('A100_80GB', 'A100 SXM4 80GB', 'NVIDIA', 'Ampere', 80, 6912, 432, 0, 765, 1410, 2039.0, 'HBM2e', 400, 'SXM4', 'Y', 7, '8.0', 2020, 'Datacenter', NULL, SYSDATE, SYSDATE);
INSERT INTO gpu_models VALUES ('H100_80GB', 'H100 PCIe 80GB', 'NVIDIA', 'Hopper', 80, 0, 528, 0, 1290, 1620, 2000.0, 'HBM3', 350, 'PCIe 5.0', 'Y', 7, '9.0', 2022, 'Datacenter', NULL, SYSDATE, SYSDATE);

-- MIG 프로필 데이터 (A100 40GB)
INSERT INTO mig_profiles VALUES ('A100_40_1G5GB', 'A100_40GB', '1g.5gb', 1, 1, 5, 7, 14.3, 'Development, Small Inference', 'A100 40GB 1-slice profile - 5GB memory');
INSERT INTO mig_profiles VALUES ('A100_40_2G10GB', 'A100_40GB', '2g.10gb', 2, 2, 10, 3, 28.6, 'Medium Training, Inference', 'A100 40GB 2-slice profile - 10GB memory');
INSERT INTO mig_profiles VALUES ('A100_40_3G20GB', 'A100_40GB', '3g.20gb', 3, 4, 20, 2, 42.9, 'Large Training, Batch Inference', 'A100 40GB 3-slice profile - 20GB memory');
INSERT INTO mig_profiles VALUES ('A100_40_7G40GB', 'A100_40GB', '7g.40gb', 7, 8, 40, 1, 100.0, 'Full GPU equivalent', 'A100 40GB full profile - 40GB memory');

-- MIG 프로필 데이터 (A100 80GB)
INSERT INTO mig_profiles VALUES ('A100_80_1G10GB', 'A100_80GB', '1g.10gb', 1, 1, 10, 7, 14.3, 'Development, Small Inference', 'A100 80GB 1-slice profile - 10GB memory');
INSERT INTO mig_profiles VALUES ('A100_80_2G20GB', 'A100_80GB', '2g.20gb', 2, 2, 20, 3, 28.6, 'Medium Training, Inference', 'A100 80GB 2-slice profile - 20GB memory');
INSERT INTO mig_profiles VALUES ('A100_80_3G40GB', 'A100_80GB', '3g.40gb', 3, 4, 40, 2, 42.9, 'Large Training, Batch Inference', 'A100 80GB 3-slice profile - 40GB memory');
INSERT INTO mig_profiles VALUES ('A100_80_7G80GB', 'A100_80GB', '7g.80gb', 7, 8, 80, 1, 100.0, 'Full GPU equivalent', 'A100 80GB full profile - 80GB memory');

-- MIG 프로필 데이터 (H100 80GB)
INSERT INTO mig_profiles VALUES ('H100_1G10GB', 'H100_80GB', '1g.10gb', 1, 1, 10, 7, 14.3, 'Development, Small Inference', 'H100 1-slice profile - 10GB memory');
INSERT INTO mig_profiles VALUES ('H100_2G20GB', 'H100_80GB', '2g.20gb', 2, 2, 20, 3, 28.6, 'Medium Training, Inference', 'H100 2-slice profile - 20GB memory');
INSERT INTO mig_profiles VALUES ('H100_3G40GB', 'H100_80GB', '3g.40gb', 3, 4, 40, 2, 42.9, 'Large Training, Batch Inference', 'H100 3-slice profile - 40GB memory');
INSERT INTO mig_profiles VALUES ('H100_7G80GB', 'H100_80GB', '7g.80gb', 7, 8, 80, 1, 100.0, 'Full GPU equivalent', 'H100 full profile - 80GB memory');

-- 워크로드 프로필 샘플 데이터
INSERT INTO workload_profiles VALUES ('WL_LLM_TRAINING', 'Large Language Model Training', 'Training', 32, 80, '8.0', 'Ampere,Hopper', 'Y', 1, '{"min_bandwidth": "1000GB/s", "tensor_cores": "required"}', '{"max_temp": 80, "power_limit": 400}', 'LOW', '{"uptime": "99%", "throughput": "high"}', 'High-performance LLM training workload', 'ai-team', SYSDATE, SYSDATE);
INSERT INTO workload_profiles VALUES ('WL_INFERENCE', 'Model Inference Service', 'Inference', 4, 16, '7.0', 'Turing,Ampere,Hopper', 'Y', 4, '{"latency": "<10ms", "throughput": "1000req/s"}', '{"shared_memory": "true"}', 'HIGH', '{"availability": "99.9%", "latency": "<10ms"}', 'Real-time inference workload', 'ml-ops', SYSDATE, SYSDATE);
INSERT INTO workload_profiles VALUES ('WL_DEVELOPMENT', 'Development Environment', 'Development', 2, 8, '6.1', 'Pascal,Turing,Ampere', 'Y', 2, '{"jupyter": "enabled", "vscode": "enabled"}', '{"time_limit": "8hours"}', 'MEDIUM', '{"availability": "95%"}', 'Development and experimentation', 'dev-team', SYSDATE, SYSDATE);
INSERT INTO workload_profiles VALUES ('WL_GAMING', 'Gaming Workload', 'Gaming', 6, 16, '6.1', 'Pascal,Turing,Ampere,Ada Lovelace', 'N', 1, '{"fps": ">60", "resolution": "4K"}', '{"exclusive": "true"}', 'MEDIUM', '{"latency": "<5ms"}', 'High-performance gaming', 'gaming-team', SYSDATE, SYSDATE);

-- 호환성 데이터 샘플
INSERT INTO gpu_compatibility VALUES ('COMPAT_GTX1080_CUDA11', 'GTX1080', '11.8', '470.57', '525.xx', '1.25+', 'containerd', 'Linux', 'Ubuntu 20.04+', 'Y', 0, NULL, NULL, SYSDATE, 'Fully supported', SYSDATE);
INSERT INTO gpu_compatibility VALUES ('COMPAT_A100_CUDA12', 'A100_80GB', '12.0', '525.60', '545.xx', '1.26+', 'containerd', 'Linux', 'Ubuntu 22.04+', 'Y', 0, NULL, NULL, SYSDATE, 'Recommended configuration', SYSDATE);
INSERT INTO gpu_compatibility VALUES ('COMPAT_H100_CUDA12', 'H100_80GB', '12.1', '535.54', NULL, '1.27+', 'containerd', 'Linux', 'Ubuntu 22.04+', 'Y', 0, NULL, NULL, SYSDATE, 'Latest drivers required', SYSDATE);

-- 알림 규칙 샘플
INSERT INTO alert_rules VALUES ('RULE_HIGH_TEMP', 'High GPU Temperature', 'TEMPERATURE', 'DEVICE', '{"exclude_models": ["GTX1080"]}', 85.0, '>', 'HIGH', 1, 'email,slack', 'Y', SYSDATE, SYSDATE);
INSERT INTO alert_rules VALUES ('RULE_LOW_UTIL', 'Low GPU Utilization', 'UTILIZATION', 'ALLOCATION', '{"min_duration": "1hour"}', 10.0, '<', 'MEDIUM', 15, 'email', 'Y', SYSDATE, SYSDATE);
INSERT INTO alert_rules VALUES ('RULE_MEMORY_FULL', 'GPU Memory Full', 'MEMORY', 'DEVICE', '{}', 95.0, '>', 'HIGH', 5, 'email,slack,webhook', 'Y', SYSDATE, SYSDATE);
INSERT INTO alert_rules VALUES ('RULE_POWER_SPIKE', 'High Power Consumption', 'POWER', 'DEVICE', '{}', 400.0, '>', 'MEDIUM', 5, 'email', 'Y', SYSDATE, SYSDATE);

COMMIT;

-- ============================================================================
-- 뷰 생성
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
            THEN (ga.release_time - ga.allocation_time) * 24 * COALESCE(ga.cost_per_hour, 0)
            WHEN ga.status = 'ALLOCATED'
            THEN (SYSDATE - ga.allocation_time) * 24 * COALESCE(ga.cost_per_hour, 0)
            ELSE COALESCE(ga.total_cost, 0)
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

-- ============================================================================
-- 저장 프로시저 생성
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

-- GPU 메트릭 수집 프로시저
CREATE OR REPLACE PROCEDURE p_collect_gpu_metrics AS
    CURSOR c_devices IS
        SELECT device_id, gpu_uuid, model_id
        FROM gpu_devices 
        WHERE device_status IN ('ACTIVE', 'MIG_ENABLED');
        
    v_metric_id VARCHAR2(50);
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
    END LOOP;
    
    COMMIT;
    
    DBMS_OUTPUT.PUT_LINE('GPU 메트릭 수집 완료: ' || c_devices%ROWCOUNT || '개 장비');
    
EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        DBMS_OUTPUT.PUT_LINE('메트릭 수집 실패: ' || SQLERRM);
        RAISE;
END p_collect_gpu_metrics;
/

-- ============================================================================
-- 트리거 생성
-- ============================================================================

-- GPU 장비 업데이트 트리거
CREATE OR REPLACE TRIGGER tr_gpu_devices_update
    BEFORE UPDATE ON gpu_devices
    FOR EACH ROW
BEGIN
    :NEW.updated_date := SYSDATE;
END;
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
END;
/

-- ============================================================================
-- 인덱스 통계 업데이트
-- ============================================================================
BEGIN
    DBMS_STATS.GATHER_TABLE_STATS(USER, 'GPU_MODELS');
    DBMS_STATS.GATHER_TABLE_STATS(USER, 'GPU_NODES');
    DBMS_STATS.GATHER_TABLE_STATS(USER, 'GPU_DEVICES');
    DBMS_STATS.GATHER_TABLE_STATS(USER, 'MIG_PROFILES');
    DBMS_STATS.GATHER_TABLE_STATS(USER, 'MIG_INSTANCES');
    DBMS_STATS.GATHER_TABLE_STATS(USER, 'GPU_ALLOCATIONS');
    DBMS_STATS.GATHER_TABLE_STATS(USER, 'GPU_USAGE_METRICS');
    DBMS_STATS.GATHER_TABLE_STATS(USER, 'GPU_COMPATIBILITY');
    DBMS_STATS.GATHER_TABLE_STATS(USER, 'GPU_BENCHMARKS');
    DBMS_STATS.GATHER_TABLE_STATS(USER, 'WORKLOAD_PROFILES');
    DBMS_STATS.GATHER_TABLE_STATS(USER, 'ALERT_RULES');
    DBMS_STATS.GATHER_TABLE_STATS(USER, 'SYSTEM_ALERTS');
END;
/

-- ============================================================================
-- 권한 설정 (선택사항)
-- ============================================================================
-- 읽기 전용 사용자를 위한 뷰 권한
-- GRANT SELECT ON v_cluster_gpu_inventory TO gpu_readonly;
-- GRANT SELECT ON v_gpu_utilization TO gpu_readonly;
-- GRANT SELECT ON v_mig_instance_status TO gpu_readonly;
-- GRANT SELECT ON v_daily_gpu_usage_stats TO gpu_readonly;
-- GRANT SELECT ON v_gpu_cost_analysis TO gpu_readonly;

-- 애플리케이션 사용자를 위한 테이블 권한
-- GRANT SELECT, INSERT, UPDATE ON gpu_allocations TO gpu_app;
-- GRANT SELECT, INSERT ON gpu_usage_metrics TO gpu_app;
-- GRANT SELECT ON gpu_models TO gpu_app;
-- GRANT SELECT ON gpu_devices TO gpu_app;
-- GRANT SELECT ON mig_profiles TO gpu_app;
-- GRANT SELECT ON mig_instances TO gpu_app;

-- ============================================================================
-- 스키마 생성 완료
-- ============================================================================
PROMPT '============================================================================';
PROMPT 'K8s GPU Management System 스키마 생성이 완료되었습니다.';
PROMPT '============================================================================';
PROMPT '생성된 테이블:';
PROMPT '- gpu_models (GPU 모델 마스터)';
PROMPT '- gpu_nodes (Kubernetes 노드)'; 
PROMPT '- gpu_devices (GPU 장비)';
PROMPT '- mig_profiles (MIG 프로필)';
PROMPT '- mig_instances (MIG 인스턴스)';
PROMPT '- gpu_allocations (GPU 할당)';
PROMPT '- gpu_usage_metrics (사용량 메트릭)';
PROMPT '- gpu_compatibility (호환성 매트릭스)';
PROMPT '- gpu_benchmarks (벤치마크 결과)';
PROMPT '- workload_profiles (워크로드 프로필)';
PROMPT '- alert_rules (알림 규칙)';
PROMPT '- system_alerts (시스템 알림)';
PROMPT '';
PROMPT '생성된 뷰:';
PROMPT '- v_cluster_gpu_inventory';
PROMPT '- v_gpu_utilization';
PROMPT '- v_mig_instance_status';
PROMPT '- v_daily_gpu_usage_stats';
PROMPT '- v_gpu_cost_analysis';
PROMPT '';
PROMPT '생성된 저장 프로시저:';
PROMPT '- p_optimize_gpu_allocation';
PROMPT '- p_create_mig_instances';
PROMPT '- p_collect_gpu_metrics';
PROMPT '============================================================================';

-- 샘플 데이터 확인 쿼리
SELECT 'GPU Models' as category, COUNT(*) as count FROM gpu_models
UNION ALL
SELECT 'MIG Profiles', COUNT(*) FROM mig_profiles  
UNION ALL
SELECT 'Workload Profiles', COUNT(*) FROM workload_profiles
UNION ALL
SELECT 'Compatibility Rules', COUNT(*) FROM gpu_compatibility
UNION ALL
SELECT 'Alert Rules', COUNT(*) FROM alert_rules;

-- EOF