package com.k8s.monitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Kubernetes 리소스 모니터링 애플리케이션 메인 클래스
 * vLLM과 SGLang 모델 서빙 Pod들의 리소스 사용량을 모니터링
 */
@SpringBootApplication
@EnableScheduling
public class K8sResourceMonitorApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(K8sResourceMonitorApplication.class, args);
    }
}