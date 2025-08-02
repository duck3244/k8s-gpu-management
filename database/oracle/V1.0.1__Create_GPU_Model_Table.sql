-- ============================================================================
-- GPU Model Master Table
-- Version: 1.0.1
-- Description: Create GPU models table with all supported GPU types
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

-- 코멘트 추가
COMMENT ON TABLE gpu_models IS 'GPU 모델 마스터 테이블 - 지원되는 모든 GPU 모델 정보';
COMMENT ON COLUMN gpu_models.model_id IS 'GPU 모델 고유 식별자';
COMMENT ON COLUMN gpu_models.architecture IS 'GPU 아키텍처 (Pascal, Turing, Ampere, Hopper, Ada Lovelace)';
COMMENT ON COLUMN gpu_models.mig_support IS 'MIG 지원 여부 (Y/N)';
COMMENT ON COLUMN gpu_models.market_segment IS '시장 세그먼트 (Gaming, Professional, Datacenter)';