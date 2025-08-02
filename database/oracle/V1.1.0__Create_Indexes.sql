-- ============================================================================
-- Create Indexes for GPU Management System
-- Version: 1.1.0
-- Description: Create optimized indexes for better query performance
-- ============================================================================

-- GPU 모델 인덱스
CREATE INDEX idx_gpu_models_arch ON gpu_models(architecture);
CREATE INDEX idx_gpu_models_segment ON gpu_models(market_segment);
CREATE INDEX idx_gpu_models_mig ON gpu_models(mig_support);

-- GPU 노드 인덱스
CREATE INDEX idx_gpu_nodes_status ON gpu_nodes(node_status);
CREATE INDEX idx_gpu_nodes_cluster ON gpu_nodes(cluster_name);

-- GPU 장비 인덱스
CREATE INDEX idx_gpu_devices_node ON gpu_devices(node_id);
CREATE INDEX idx_gpu_devices_model ON gpu_devices(model_id);
CREATE INDEX idx_gpu_devices_status ON gpu_devices(device_status);
CREATE INDEX idx_gpu_devices_uuid ON gpu_devices(gpu_uuid);

-- MIG 프로필 인덱스
CREATE INDEX idx_mig_profiles_model ON mig_profiles(model_id);

-- MIG 인스턴스 인덱스
CREATE INDEX idx_mig_instances_device ON mig_instances(device_id);
CREATE INDEX idx_mig_instances_profile ON mig_instances(profile_id);
CREATE INDEX idx_mig_instances_allocated ON mig_instances(allocated);

-- GPU 할당 인덱스
CREATE INDEX idx_gpu_allocations_namespace ON gpu_allocations(namespace);
CREATE INDEX idx_gpu_allocations_status ON gpu_allocations(status);
CREATE INDEX idx_gpu_allocations_resource ON gpu_allocations(allocated_resource);
CREATE INDEX idx_gpu_allocations_time ON gpu_allocations(allocation_time);
CREATE INDEX idx_gpu_allocations_user ON gpu_allocations(user_id);

-- GPU 메트릭 인덱스 (성능 최적화 중요)
CREATE INDEX idx_gpu_metrics_device_time ON gpu_usage_metrics(device_id, timestamp);
CREATE INDEX idx_gpu_metrics_mig_time ON gpu_usage_metrics(mig_id, timestamp);
CREATE INDEX idx_gpu_metrics_timestamp ON gpu_usage_metrics(timestamp);

-- 호환성 인덱스
CREATE INDEX idx_gpu_compat_model ON gpu_compatibility(model_id);
CREATE INDEX idx_gpu_compat_cuda ON gpu_compatibility(cuda_version);

-- 벤치마크 인덱스
CREATE INDEX idx_gpu_benchmark_model ON gpu_benchmarks(model_id);
CREATE INDEX idx_gpu_benchmark_type ON gpu_benchmarks(benchmark_type);
CREATE INDEX idx_gpu_benchmark_date ON gpu_benchmarks(test_date);

-- 워크로드 프로필 인덱스
CREATE INDEX idx_workload_profiles_type ON workload_profiles(workload_type);
CREATE INDEX idx_workload_profiles_memory ON workload_profiles(min_memory_gb);

-- 알림 인덱스
CREATE INDEX idx_system_alerts_status ON system_alerts(status);
CREATE INDEX idx_system_alerts_severity ON system_alerts(severity);
CREATE INDEX idx_system_alerts_date ON system_alerts(created_date);

-- 복합 인덱스 (자주 사용되는 쿼리 패턴용)
CREATE INDEX idx_gpu_devices_node_status ON gpu_devices(node_id, device_status);
CREATE INDEX idx_gpu_allocations_status_time ON gpu_allocations(status, allocation_time);
CREATE INDEX idx_mig_instances_device_allocated ON mig_instances(device_id, allocated);

-- 함수 기반 인덱스 (날짜 기반 쿼리 최적화)
CREATE INDEX idx_gpu_metrics_date_only ON gpu_usage_metrics(TRUNC(timestamp));
CREATE INDEX idx_gpu_allocations_date_only ON gpu_allocations(TRUNC(allocation_time));