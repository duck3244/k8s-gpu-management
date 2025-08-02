-- ============================================================================
-- GPU Device Table
-- Version: 1.0.3
-- Description: Create GPU devices table for individual GPU management
-- ============================================================================

CREATE TABLE gpu_devices (
    device_id VARCHAR2(50) PRIMARY KEY,
    node_id VARCHAR2(50) NOT NULL,
    model_id VARCHAR2(20) NOT NULL,
    device_index NUMBER(2) NOT NULL, -- GPU index on node (0, 1, 2, ...)
    serial_number VARCHAR2(50),
    pci_address VARCHAR2(20) NOT NULL,
    gpu_uuid VARCHAR2(100) UNIQUE NOT NULL,
    device_status VARCHAR2(20) DEFAULT 'ACTIVE' 
        CHECK (device_status IN ('ACTIVE', 'INACTIVE', 'MAINTENANCE', 'FAILED', 'MIG_ENABLED')),
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

-- 코멘트 추가
COMMENT ON TABLE gpu_devices IS 'GPU 장비 상세 정보 테이블';
COMMENT ON COLUMN gpu_devices.device_index IS '노드 내 GPU 인덱스 (0부터 시작)';
COMMENT ON COLUMN gpu_devices.gpu_uuid IS 'GPU 고유 UUID (nvidia-smi에서 제공)';
COMMENT ON COLUMN gpu_devices.device_status IS '장비 상태 (ACTIVE, INACTIVE, MAINTENANCE, FAILED, MIG_ENABLED)';