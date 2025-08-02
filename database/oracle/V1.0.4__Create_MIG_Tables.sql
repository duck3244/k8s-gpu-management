-- ============================================================================
-- MIG (Multi-Instance GPU) Tables
-- Version: 1.0.4
-- Description: Create MIG profiles and instances tables for A100/H100 GPUs
-- ============================================================================

-- MIG 프로필 테이블
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

-- MIG 인스턴스 테이블
CREATE TABLE mig_instances (
    mig_id VARCHAR2(50) PRIMARY KEY,
    device_id VARCHAR2(50) NOT NULL,
    profile_id VARCHAR2(20) NOT NULL,
    instance_id NUMBER(2) NOT NULL, -- GPU 내에서 MIG 인스턴스 ID (0-6)
    mig_uuid VARCHAR2(100) UNIQUE NOT NULL,
    allocated CHAR(1) DEFAULT 'N' CHECK (allocated IN ('Y', 'N')),
    instance_status VARCHAR2(20) DEFAULT 'ACTIVE' 
        CHECK (instance_status IN ('ACTIVE', 'INACTIVE', 'FAILED')),
    created_date DATE DEFAULT SYSDATE,
    allocated_date DATE,
    last_used_date DATE,
    CONSTRAINT fk_mig_instances_device FOREIGN KEY (device_id) REFERENCES gpu_devices(device_id),
    CONSTRAINT fk_mig_instances_profile FOREIGN KEY (profile_id) REFERENCES mig_profiles(profile_id),
    CONSTRAINT uk_mig_instances_device_id UNIQUE (device_id, instance_id)
);

-- 코멘트 추가
COMMENT ON TABLE mig_profiles IS 'MIG 프로필 정의 테이블 (A100, H100 전용)';
COMMENT ON TABLE mig_instances IS 'MIG 인스턴스 관리 테이블';
COMMENT ON COLUMN mig_profiles.profile_name IS 'MIG 프로필명 (예: 1g.5gb, 2g.10gb)';
COMMENT ON COLUMN mig_profiles.performance_ratio IS '전체 GPU 대비 성능 비율';
COMMENT ON COLUMN mig_instances.instance_id IS 'GPU 내 MIG 인스턴스 ID (0-6)';