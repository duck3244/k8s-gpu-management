-- ============================================================================
-- Insert Sample Data for GPU Management System
-- Version: 1.5.0
-- Description: Insert sample data for testing and demonstration
-- ============================================================================

-- GPU 모델 데이터 (14종 지원)
INSERT ALL
    INTO gpu_models VALUES ('GTX1080', 'GeForce GTX 1080', 'NVIDIA', 'Pascal', 8, 2560, 0, 0, 1607, 1733, 320.0, 'GDDR5X', 180, 'PCIe 3.0', 'N', 0, '6.1', 2016, 'Gaming', TO_DATE('2021-12-31', 'YYYY-MM-DD'), SYSDATE, SYSDATE)
    INTO gpu_models VALUES ('GTX1080TI', 'GeForce GTX 1080 Ti', 'NVIDIA', 'Pascal', 11, 3584, 0, 0, 1480, 1582, 484.0, 'GDDR5X', 250, 'PCIe 3.0', 'N', 0, '6.1', 2017, 'Gaming', TO_DATE('2022-12-31', 'YYYY-MM-DD'), SYSDATE, SYSDATE)
    INTO gpu_models VALUES ('TITANXP', 'Titan Xp', 'NVIDIA', 'Pascal', 12, 3840, 0, 0, 1417, 1531, 547.7, 'GDDR5X', 250, 'PCIe 3.0', 'N', 0, '6.1', 2017, 'Professional', TO_DATE('2022-12-31', 'YYYY-MM-DD'), SYSDATE, SYSDATE)
    INTO gpu_models VALUES ('RTX2080', 'GeForce RTX 2080', 'NVIDIA', 'Turing', 8, 2944, 368, 46, 1515, 1710, 448.0, 'GDDR6', 215, 'PCIe 3.0', 'N', 0, '7.5', 2018, 'Gaming', NULL, SYSDATE, SYSDATE)
    INTO gpu_models VALUES ('RTX2080TI', 'GeForce RTX 2080 Ti', 'NVIDIA', 'Turing', 11, 4352, 544, 68, 1350, 1545, 616.0, 'GDDR6', 250, 'PCIe 3.0', 'N', 0, '7.5', 2018, 'Gaming', NULL, SYSDATE, SYSDATE)
    INTO gpu_models VALUES ('RTX3080', 'GeForce RTX 3080', 'NVIDIA', 'Ampere', 10, 8704, 272, 68, 1440, 1710, 760.3, 'GDDR6X', 320, 'PCIe 4.0', 'N', 0, '8.6', 2020, 'Gaming', NULL, SYSDATE, SYSDATE)
    INTO gpu_models VALUES ('RTX3090', 'GeForce RTX 3090', 'NVIDIA', 'Ampere', 24, 10496, 328, 82, 1395, 1695, 936.2, 'GDDR6X', 350, 'PCIe 4.0', 'N', 0, '8.6', 2020, 'Gaming', NULL, SYSDATE, SYSDATE)
    INTO gpu_models VALUES ('RTX4080', 'GeForce RTX 4080', 'NVIDIA', 'Ada Lovelace', 16, 9728, 304, 76, 1260, 2505, 716.8, 'GDDR6X', 320, 'PCIe 4.0', 'N', 0, '8.9', 2022, 'Gaming', NULL, SYSDATE, SYSDATE)
    INTO gpu_models VALUES ('RTX4090', 'GeForce RTX 4090', 'NVIDIA', 'Ada Lovelace', 24, 16384, 512, 128, 1230, 2520, 1008.0, 'GDDR6X', 450, 'PCIe 4.0', 'N', 0, '8.9', 2022, 'Gaming', NULL, SYSDATE, SYSDATE)
    INTO gpu_models VALUES ('V100_16GB', 'Tesla V100 PCIe 16GB', 'NVIDIA', 'Volta', 16, 5120, 640, 0, 1245, 1380, 900.0, 'HBM2', 250, 'PCIe 3.0', 'N', 0, '7.0', 2017, 'Datacenter', NULL, SYSDATE, SYSDATE)
    INTO gpu_models VALUES ('V100_32GB', 'Tesla V100 SXM2 32GB', 'NVIDIA', 'Volta', 32, 5120, 640, 0, 1290, 1530, 900.0, 'HBM2', 300, 'SXM2', 'N', 0, '7.0', 2017, 'Datacenter', NULL, SYSDATE, SYSDATE)
    INTO gpu_models VALUES ('A100_40GB', 'A100 PCIe 40GB', 'NVIDIA', 'Ampere', 40, 6912, 432, 0, 765, 1065, 1555.0, 'HBM2e', 250, 'PCIe 4.0', 'Y', 7, '8.0', 2020, 'Datacenter', NULL, SYSDATE, SYSDATE)
    INTO gpu_models VALUES ('A100_80GB', 'A100 SXM4 80GB', 'NVIDIA', 'Ampere', 80, 6912, 432, 0, 765, 1410, 2039.0, 'HBM2e', 400, 'SXM4', 'Y', 7, '8.0', 2020, 'Datacenter', NULL, SYSDATE, SYSDATE)
    INTO gpu_models VALUES ('H100_80GB', 'H100 PCIe 80GB', 'NVIDIA', 'Hopper', 80, 0, 528, 0, 1290, 1620, 2000.0, 'HBM3', 350, 'PCIe 5.0', 'Y', 7, '9.0', 2022, 'Datacenter', NULL, SYSDATE, SYSDATE)
SELECT * FROM dual;

-- MIG 프로필 데이터
INSERT ALL
    -- A100 40GB 프로필
    INTO mig_profiles VALUES ('A100_40_1G5GB', 'A100_40GB', '1g.5gb', 1, 1, 5, 7, 14.3, 'Development, Small Inference', 'A100 40GB 1-slice profile - 5GB memory')
    INTO mig_profiles VALUES ('A100_40_2G10GB', 'A100_40GB', '2g.10gb', 2, 2, 10, 3, 28.6, 'Medium Training, Inference', 'A100 40GB 2-slice profile - 10GB memory')
    INTO mig_profiles VALUES ('A100_40_3G20GB', 'A100_40GB', '3g.20gb', 3, 4, 20, 2, 42.9, 'Large Training, Batch Inference', 'A100 40GB 3-slice profile - 20GB memory')
    INTO mig_profiles VALUES ('A100_40_7G40GB', 'A100_40GB', '7g.40gb', 7, 8, 40, 1, 100.0, 'Full GPU equivalent', 'A100 40GB full profile - 40GB memory')
    -- A100 80GB 프로필
    INTO mig_profiles VALUES ('A100_80_1G10GB', 'A100_80GB', '1g.10gb', 1, 1, 10, 7, 14.3, 'Development, Small Inference', 'A100 80GB 1-slice profile - 10GB memory')
    INTO mig_profiles VALUES ('A100_80_2G20GB', 'A100_80GB', '2g.20gb', 2, 2, 20, 3, 28.6, 'Medium Training, Inference', 'A100 80GB 2-slice profile - 20GB memory')
    INTO mig_profiles VALUES ('A100_80_3G40GB', 'A100_80GB', '3g.40gb', 3, 4, 40, 2, 42.9, 'Large Training, Batch Inference', 'A100 80GB 3-slice profile - 40GB memory')
    INTO mig_profiles VALUES ('A100_80_7G80GB', 'A100_80GB', '7g.80gb', 7, 8, 80, 1, 100.0, 'Full GPU equivalent', 'A100 80GB full profile - 80GB memory')
    -- H100 80GB 프로필
    INTO mig_profiles VALUES ('H100_1G10GB', 'H100_80GB', '1g.10gb', 1, 1, 10, 7, 14.3, 'Development, Small Inference', 'H100 1-slice profile - 10GB memory')
    INTO mig_profiles VALUES ('H100_2G20GB', 'H100_80GB', '2g.20gb', 2, 2, 20, 3, 28.6, 'Medium Training, Inference', 'H100 2-slice profile - 20GB memory')
    INTO mig_profiles VALUES ('H100_3G40GB', 'H100_80GB', '3g.40gb', 3, 4, 40, 2, 42.9, 'Large Training, Batch Inference', 'H100 3-slice profile - 40GB memory')
    INTO mig_profiles VALUES ('H100_7G80GB', 'H100_80GB', '7g.80gb', 7, 8, 80, 1, 100.0, 'Full GPU equivalent', 'H100 full profile - 80GB memory')
SELECT * FROM dual;

-- 워크로드 프로필 샘플 데이터
INSERT ALL
    INTO workload_profiles VALUES ('WL_LLM_TRAINING', 'Large Language Model Training', 'Training', 32, 80, '8.0', 'Ampere,Hopper', 'Y', 1, '{"min_bandwidth": "1000GB/s", "tensor_cores": "required"}', '{"max_temp": 80, "power_limit": 400}', 'LOW', '{"uptime": "99%", "throughput": "high"}', 'High-performance LLM training workload', 'ai-team', SYSDATE, SYSDATE)
    INTO workload_profiles VALUES ('WL_INFERENCE', 'Model Inference Service', 'Inference', 4, 16, '7.0', 'Turing,Ampere,Hopper', 'Y', 4, '{"latency": "<10ms", "throughput": "1000req/s"}', '{"shared_memory": "true"}', 'HIGH', '{"availability": "99.9%", "latency": "<10ms"}', 'Real-time inference workload', 'ml-ops', SYSDATE, SYSDATE)
    INTO workload_profiles VALUES ('WL_DEVELOPMENT', 'Development Environment', 'Development', 2, 8, '6.1', 'Pascal,Turing,Ampere', 'Y', 2, '{"jupyter": "enabled", "vscode": "enabled"}', '{"time_limit": "8hours"}', 'MEDIUM', '{"availability": "95%"}', 'Development and experimentation', 'dev-team', SYSDATE, SYSDATE)
    INTO workload_profiles VALUES ('WL_GAMING', 'Gaming Workload', 'Gaming', 6, 16, '6.1', 'Pascal,Turing,Ampere,Ada Lovelace', 'N', 1, '{"fps": ">60", "resolution": "4K"}', '{"exclusive": "true"}', 'MEDIUM', '{"latency": "<5ms"}', 'High-performance gaming', 'gaming-team', SYSDATE, SYSDATE)
SELECT * FROM dual;

-- 호환성 데이터 샘플
INSERT ALL
    INTO gpu_compatibility VALUES ('COMPAT_GTX1080_CUDA11', 'GT