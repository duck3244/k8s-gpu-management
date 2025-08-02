package com.k8s.monitor.config;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.util.Config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Kubernetes 클라이언트 설정
 * 클러스터 내부/외부 실행 환경에 따라 자동으로 설정 선택
 */
@Configuration
@Slf4j
public class KubernetesConfig {

    /**
     * Kubernetes API 클라이언트 설정
     * @return ApiClient 인스턴스
     * @throws IOException 설정 로드 실패 시
     */
    @Bean
    public ApiClient kubernetesApiClient() throws IOException {
        ApiClient client;
        try {
            // 클러스터 내부 설정을 먼저 시도
            client = Config.fromCluster();
            log.info("Using in-cluster Kubernetes configuration");
        } catch (IOException e) {
            // 실패 시 기본 설정 사용 (보통 ~/.kube/config)
            client = Config.defaultClient();
            log.info("Using default Kubernetes configuration from ~/.kube/config");
        }
        
        // 전역 기본 클라이언트로 설정
        io.kubernetes.client.openapi.Configuration.setDefaultApiClient(client);
        return client;
    }

    /**
     * Core V1 API (Pod, Node, Service 등)
     */
    @Bean
    public CoreV1Api coreV1Api(ApiClient apiClient) {
        return new CoreV1Api(apiClient);
    }

    /**
     * Apps V1 API (Deployment, ReplicaSet 등)
     */
    @Bean
    public AppsV1Api appsV1Api(ApiClient apiClient) {
        return new AppsV1Api(apiClient);
    }
}