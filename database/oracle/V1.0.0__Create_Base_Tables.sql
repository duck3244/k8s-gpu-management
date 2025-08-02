-- ============================================================================
-- K8s GPU Management System - Base Setup
-- Version: 1.0.0
-- Description: Create sequences and basic setup
-- ============================================================================

-- 기존 객체 정리 (개발 환경용)
-- 운영 환경에서는 주석 처리하거나 제거

-- 시퀀스 생성
CREATE SEQUENCE seq_gpu_allocation_id 
START WITH 1 
INCREMENT BY 1 
NOCACHE;

CREATE SEQUENCE seq_metric_id 
START WITH 1 
INCREMENT BY 1 
NOCACHE;

CREATE SEQUENCE seq_benchmark_id 
START WITH 1 
INCREMENT BY 1 
NOCACHE;

CREATE SEQUENCE seq_alert_id 
START WITH 1 
INCREMENT BY 1 
NOCACHE;

-- 기본 설정 확인
SELECT 'Base setup completed' as status FROM dual;