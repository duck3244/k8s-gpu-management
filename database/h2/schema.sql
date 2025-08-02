---

## 📁 H2 개발용 스키마

### `h2/schema.sql` (개발용 간소화 스키마)

```sql
-- ============================================================================
-- H2 Development Schema for GPU Management System
-- Simplified schema for development and testing
-- ============================================================================

-- 시퀀스 생성 (H2 문법)
CREATE SEQUENCE IF NOT EXISTS seq_gpu_allocation_id START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS seq_metric_id START WITH 1 INCREMENT BY 1;

-- GPU 모델 테이블 (간소화)
CREATE TABLE IF NOT EXISTS gpu_models (
    model_id VARCHAR(20) PRIMARY KEY,
    model_name VARCHAR(50) NOT NULL,
    architecture VARCHAR(30) NOT NULL,
    memory_gb INT NOT NULL,
    mig_support CHAR(1) DEFAULT 'N',
    market_segment VARCHAR(20),
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP()
);

-- GPU 노드 테이블 (간소화)
CREATE TABLE IF NOT EXISTS gpu_nodes (
    node_id VARCHAR(50) PRIMARY KEY,
    node_name VARCHAR(100) NOT NULL,
    cluster_name VARCHAR(50),
    total_gpus INT DEFAULT 0,
    available_gpus INT DEFAULT 0,
    node_status VARCHAR(20) DEFAULT 'ACTIVE',
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP()
);

-- GPU 장비 테이블 (간소화)
CREATE TABLE IF NOT EXISTS gpu_devices (
    device_id VARCHAR(50) PRIMARY KEY,
    node_id VARCHAR(50) NOT NULL,
    model_id VARCHAR(20) NOT NULL,
    device_index INT NOT NULL,
    gpu_uuid VARCHAR(100) UNIQUE NOT NULL,
    device_status VARCHAR(20) DEFAULT 'ACTIVE',
    current_temp_c DECIMAL(5,2),
    current_power_w DECIMAL(6,2),
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP(),
    FOREIGN KEY (node_id) REFERENCES gpu_nodes(node_id),
    FOREIGN KEY (model_id) REFERENCES gpu_models(model_id)
);

-- MIG 프로필 테이블 (간소화)
CREATE TABLE IF NOT EXISTS mig_profiles (
    profile_id VARCHAR(20) PRIMARY KEY,
    model_id VARCHAR(20) NOT NULL,
    profile_name VARCHAR(50) NOT NULL,
    memory_gb INT NOT NULL,
    max_instances_per_gpu INT NOT NULL,
    FOREIGN KEY (model_id) REFERENCES gpu_models(model_id)
);

-- MIG 인스턴스 테이블 (간소화)
CREATE TABLE IF NOT EXISTS mig_instances (
    mig_id VARCHAR(50) PRIMARY KEY,
    device_id VARCHAR(50) NOT NULL,
    profile_id VARCHAR(20) NOT NULL,
    instance_id INT NOT NULL,
    mig_uuid VARCHAR(100) UNIQUE NOT NULL,
    allocated CHAR(1) DEFAULT 'N',
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP(),
    FOREIGN KEY (device_id) REFERENCES gpu_devices(device_id),
    FOREIGN KEY (profile_id) REFERENCES mig_profiles(profile_id)
);

-- GPU 할당 테이블 (간소화)
CREATE TABLE IF NOT EXISTS gpu_allocations (
    allocation_id VARCHAR(50) PRIMARY KEY,
    namespace VARCHAR(50) NOT NULL,
    pod_name VARCHAR(100) NOT NULL,
    resource_type VARCHAR(20) NOT NULL,
    allocated_resource VARCHAR(50) NOT NULL,
    status VARCHAR(20) DEFAULT 'ALLOCATED',
    allocation_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP(),
    cost_per_hour DECIMAL(8,4),
    user_id VARCHAR(50)
);

-- GPU 메트릭 테이블 (간소화)
CREATE TABLE IF NOT EXISTS gpu_usage_metrics (
    metric_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(50),
    gpu_utilization_pct DECIMAL(5,2),
    memory_utilization_pct DECIMAL(5,2),
    temperature_c DECIMAL(5,2),
    power_draw_w DECIMAL(6,2),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP(),
    FOREIGN KEY (device_id) REFERENCES gpu_devices(device_id)
);

-- 샘플 데이터 (H2용)
INSERT INTO gpu_models VALUES 
    ('RTX4090', 'GeForce RTX 4090', 'Ada Lovelace', 24, 'N', 'Gaming', CURRENT_TIMESTAMP()),
    ('A100_80GB', 'A100 SXM4 80GB', 'Ampere', 80, 'Y', 'Datacenter', CURRENT_TIMESTAMP()),
    ('H100_80GB', 'H100 PCIe 80GB', 'Hopper', 80, 'Y', 'Datacenter', CURRENT_TIMESTAMP());

INSERT INTO gpu_nodes VALUES 
    ('node-01', 'gpu-node-01', 'dev-cluster', 2, 2, 'ACTIVE', CURRENT_TIMESTAMP()),
    ('node-02', 'gpu-node-02', 'dev-cluster', 1, 1, 'ACTIVE', CURRENT_TIMESTAMP());

-- 인덱스 생성 (H2용)
CREATE INDEX IF NOT EXISTS idx_gpu_devices_node ON gpu_devices(node_id);
CREATE INDEX IF NOT EXISTS idx_gpu_devices_status ON gpu_devices(device_status);
CREATE INDEX IF NOT EXISTS idx_gpu_allocations_status ON gpu_allocations(status);
CREATE INDEX IF NOT EXISTS idx_gpu_metrics_timestamp ON gpu_usage_metrics(timestamp);