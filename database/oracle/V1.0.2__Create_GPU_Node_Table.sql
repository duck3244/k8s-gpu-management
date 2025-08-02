-- ============================================================================
-- Kubernetes GPU Node Table
-- Version: 1.0.2
-- Description: Create Kubernetes nodes table for GPU cluster management
-- ============================================================================

CREATE TABLE gpu_nodes (
    node_id VARCHAR2(50) PRIMARY KEY,
    node_name VARCHAR2(100) NOT NULL,
    cluster_name VARCHAR2(50),
    node_ip VARCHAR2(15),
    total_gpus NUMBER(3) DEFAULT 0,
    available_gpus NUMBER(3) DEFAULT 0,
    node_status VARCHAR2(20) DEFAULT 'ACTIVE' 
        CHECK (node_status IN ('ACTIVE', 'INACTIVE', 'MAINTENANCE', 'FAILED')),
    kubernetes_version VARCHAR2(20),
    docker_version VARCHAR2(20),
    nvidia_driver_version VARCHAR2(20),
    node_labels CLOB, -- JSON format for node labels
    taints CLOB, -- JSON format for node taints
    created_date DATE DEFAULT SYSDATE,
    updated_date DATE DEFAULT SYSDATE
);

-- 코멘트 추가
COMMENT ON TABLE gpu_nodes IS 'Kubernetes GPU 노드 정보 테이블';
COMMENT ON COLUMN gpu_nodes.node_labels IS 'Kubernetes 노드 레이블 (JSON 형식)';
COMMENT ON COLUMN gpu_nodes.taints IS 'Kubernetes 노드 테인트 (JSON 형식)';
COMMENT ON COLUMN gpu_nodes.node_status IS '노드 상태 (ACTIVE, INACTIVE, MAINTENANCE, FAILED)';