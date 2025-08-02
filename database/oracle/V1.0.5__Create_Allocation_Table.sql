-- ============================================================================
-- GPU Allocation Table
-- Version: 1.0.5
-- Description: Create GPU resource allocation management table
-- ============================================================================

CREATE TABLE gpu_allocations (
    allocation_id VARCHAR2(50) PRIMARY KEY,
    namespace VARCHAR2(50) NOT NULL,
    pod_name VARCHAR2(100) NOT NULL,
    container_name VARCHAR2(100),
    workload_type VARCHAR2(30), -- Training, Inference, Development, Gaming
    resource_type VARCHAR2(20) NOT NULL 
        CHECK (resource_type IN ('FULL_GPU', 'MIG_INSTANCE', 'SHARED_GPU')),
    allocated_resource VARCHAR2(50) NOT NULL, -- device_id or mig_id
    requested_memory_gb NUMBER(3),
    allocated_memory_gb NUMBER(3),
    priority_class VARCHAR2(20) DEFAULT 'normal',
    allocation_time DATE DEFAULT SYSDATE,
    planned_release_time DATE,
    release_time DATE,
    status VARCHAR2(20) DEFAULT 'ALLOCATED' 
        CHECK (status IN ('PENDING', 'ALLOCATED', 'RELEASED', 'FAILED', 'EXPIRED')),
    cost_per_hour NUMBER(8,4),
    total_cost NUMBER(10,2),
    user_id VARCHAR2(50),
    team_id VARCHAR2(50),
    project_id VARCHAR2(50),
    created_date DATE DEFAULT SYSDATE,
    updated_date DATE DEFAULT SYSDATE
);

-- 코멘트 추가
COMMENT ON TABLE gpu_allocations IS 'GPU 리소스 할당 관리 테이블';
COMMENT ON COLUMN gpu_allocations.resource_type IS '리소스 타입 (FULL_GPU, MIG_INSTANCE, SHARED_GPU)';
COMMENT ON COLUMN gpu_allocations.allocated_resource IS '할당된 리소스 ID (device_id 또는 mig_id)';
COMMENT ON COLUMN gpu_allocations.workload_type IS '워크로드 타입 (Training, Inference, Development, Gaming)';
COMMENT ON COLUMN gpu_allocations.status IS '할당 상태 (PENDING, ALLOCATED, RELEASED, FAILED, EXPIRED)';