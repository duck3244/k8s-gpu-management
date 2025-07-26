# Kubernetes Resource Monitor

vLLM과 SGLang 모델 서빙을 위한 Kubernetes 리소스 모니터링 시스템

## 🚀 주요 기능

- **실시간 리소스 모니터링**: Pod, Node의 CPU, 메모리, GPU 사용량 실시간 추적
- **모델 서빙 특화**: vLLM과 SGLang Pod 자동 식별 및 분류
- **웹 대시보드**: 직관적인 리소스 사용률 시각화
- **알람 시스템**: 리소스 사용률 임계값 기반 알람
- **API 제공**: RESTful API를 통한 프로그래매틱 접근
- **메트릭 수집**: 시계열 데이터 저장 및 분석
- **확장성**: Kubernetes 환경에서 고가용성 구성

## 📋 시스템 요구사항

### 필수 요구사항
- **Kubernetes**: v1.20 이상
- **Java**: OpenJDK 17 이상
- **Maven**: 3.6 이상
- **Docker**: 20.10 이상

### 선택사항
- **Metrics Server**: 실시간 메트릭 수집용
- **Prometheus**: 모니터링 및 알람용
- **PostgreSQL**: 프로덕션 환경용 데이터베이스
- **Redis**: 캐싱 성능 향상용

## 🏗️ 프로젝트 구조

```
k8s-resource-monitor/
├── src/main/java/com/k8s/monitor/
│   ├── K8sResourceMonitorApplication.java     # 메인 애플리케이션
│   ├── config/
│   │   └── KubernetesConfig.java              # K8s 클라이언트 설정
│   ├── controller/
│   │   └── ResourceController.java            # REST API 컨트롤러
│   ├── service/
│   │   ├── KubernetesService.java             # K8s API 연동
│   │   ├── ResourceMetricsService.java        # 메트릭 수집
│   │   ├── ResourceAnalysisService.java       # 데이터 분석
│   │   └── MetricsCollectionService.java      # 스케줄링된 수집
│   ├── dto/
│   │   ├── PodResourceInfo.java               # Pod DTO
│   │   ├── NodeResourceInfo.java              # Node DTO
│   │   └── ResourceUsageResponse.java         # 응답 DTO
│   ├── model/
│   │   └── ResourceMetrics.java               # JPA 엔티티
│   └── repository/
│       └── MetricsRepository.java             # 데이터 접근 계층
├── src/main/resources/
│   ├── application.yml                        # 애플리케이션 설정
│   └── static/
│       └── index.html                         # 웹 대시보드
├── k8s/
│   └── deployment.yaml                        # K8s 배포 설정
├── Dockerfile                                 # 컨테이너 이미지
├── pom.xml                                    # Maven 설정
└── README.md                                  # 프로젝트 문서
```

## 🚀 빠른 시작

### 1. 프로젝트 클론 및 빌드

```bash
# 프로젝트 클론
git clone https://github.com/company/k8s-resource-monitor.git
cd k8s-resource-monitor

# Maven 빌드
mvn clean package -DskipTests

# Docker 이미지 빌드
docker build -t k8s-resource-monitor:latest .
```

### 2. 로컬 개발 환경 실행

```bash
# kubeconfig 설정 확인
kubectl config current-context

# 애플리케이션 실행
java -jar target/k8s-resource-monitor-1.0.0.jar

# 웹 대시보드 접속
open http://localhost:8080/k8s-monitor
```

### 3. Kubernetes 클러스터 배포

```bash
# 네임스페이스 및 RBAC 생성
kubectl apply -f k8s/deployment.yaml

# 배포 상태 확인
kubectl get pods -n k8s-monitoring

# 서비스 포트 포워딩
kubectl port-forward -n k8s-monitoring service/k8s-monitor-service 8080:80

# 웹 대시보드 접속
open http://localhost:8080/k8s-monitor
```

## 🔧 설정

### application.yml 주요 설정

```yaml
k8s:
  monitor:
    # 메트릭 수집 간격 (초)
    collection-interval: 30
    
    # 데이터 보존 기간 (일)
    retention-days: 7
    
    # 모니터링 대상 네임스페이스
    namespaces:
      - default
      - model-serving
      - vllm
      - sglang
    
    # 알람 임계값
    alerts:
      cpu-threshold: 80.0
      memory-threshold: 80.0
      gpu-threshold: 80.0
```

### 환경 변수

| 변수명 | 설명 | 기본값 |
|--------|------|--------|
| `SPRING_PROFILES_ACTIVE` | 활성 프로파일 | `development` |
| `JAVA_OPTS` | JVM 옵션 | `-Xms512m -Xmx1024m` |
| `DB_USERNAME` | 데이터베이스 사용자명 | `k8s_monitor` |
| `DB_PASSWORD` | 데이터베이스 비밀번호 | `password` |

## 📊 API 사용법

### 주요 API 엔드포인트

```bash
# Pod 리소스 정보 조회
GET /api/v1/resources/pods?namespace=default

# Node 리소스 정보 조회
GET /api/v1/resources/nodes

# 통합 리소스 사용량 조회
GET /api/v1/resources/usage

# 특정 Pod 상세 정보
GET /api/v1/resources/pods/{namespace}/{podName}

# 리소스 알람 조회
GET /api/v1/resources/alerts

# 통계 정보 조회
GET /api/v1/resources/statistics?hours=24
```

### API 응답 예시

```json
{
  "pods": [
    {
      "name": "vllm-model-server-abc123",
      "namespace": "model-serving",
      "nodeName": "worker-node-1",
      "modelType": "vllm",
      "phase": "Running",
      "cpuUsagePercent": 75.5,
      "memoryUsagePercent": 68.2,
      "gpuUsagePercent": 85.0,
      "creationTime": "2024-01-15T10:30:00"
    }
  ],
  "clusterSummary": {
    "totalNodes": 3,
    "totalPods": 15,
    "vllmPods": 8,
    "sglangPods": 7,
    "avgCpuUsage": 65.5,
    "avgMemoryUsage": 72.1,
    "avgGpuUsage": 78.9
  }
}
```

## 🖥️ 웹 대시보드

### 주요 기능
- **실시간 모니터링**: 30초마다 자동 새로고침
- **필터링**: 네임스페이스, 모델 타입별 필터
- **시각화**: 프로그레스 바를 통한 사용률 표시
- **알람**: 실시간 리소스 알람 표시
- **통계**: 시간별, 모델별 사용량 통계

### 대시보드 섹션
1. **요약 카드**: 클러스터 전체 통계
2. **Pod 테이블**: 모델 서빙 Pod 목록 및 리소스 사용량
3. **Node 테이블**: 노드별 리소스 현황
4. **알람**: 현재 활성 알람 목록
5. **통계**: 시간별, 모델별 사용량 분석

## 🔒 보안 설정

### RBAC 권한
```yaml
# 필요한 최소 권한
rules:
- apiGroups: [""]
  resources: ["pods", "nodes", "namespaces"]
  verbs: ["get", "list", "watch"]
- apiGroups: ["apps"]
  resources: ["deployments", "replicasets"]
  verbs: ["get", "list", "watch"]
- apiGroups: ["metrics.k8s.io"]
  resources: ["pods", "nodes"]
  verbs: ["get", "list"]
```

### 네트워크 정책
```yaml
# 필요한 포트만 허용
ingress:
- ports:
  - protocol: TCP
    port: 8080
egress:
- ports:
  - protocol: TCP
    port: 443  # K8s API
  - protocol: TCP
    port: 53   # DNS
```

## 📈 모니터링 및 알람

### Prometheus 메트릭
- `k8s_monitor_collection_duration_seconds`: 메트릭 수집 소요 시간
- `k8s_monitor_pods_total`: 모니터링 중인 Pod 수
- `k8s_monitor_alerts_total`: 활성 알람 수

### 알람 규칙
- **CPU 사용률 > 80%**: WARNING
- **메모리 사용률 > 80%**: WARNING
- **GPU 사용률 > 90%**: CRITICAL
- **Pod 상태 != Running**: WARNING

## 🚀 프로덕션 배포

### 1. 데이터베이스 설정 (PostgreSQL)

```yaml
# PostgreSQL 설정
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/k8s_monitor
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
```

### 2. 리소스 요구사항

```yaml
resources:
  requests:
    memory: "512Mi"
    cpu: "250m"
  limits:
    memory: "1Gi"
    cpu: "500m"
```

### 3. 고가용성 설정

```yaml
# 멀티 레플리카
replicas: 3

# Pod Anti-Affinity
affinity:
  podAntiAffinity:
    preferredDuringSchedulingIgnoredDuringExecution:
    - weight: 100
      podAffinityTerm:
        labelSelector:
          matchLabels:
            app: k8s-resource-monitor
        topologyKey: kubernetes.io/hostname
```

## 🔍 트러블슈팅

### 일반적인 문제

#### 1. Metrics Server 연결 실패
```bash
# Metrics Server 상태 확인
kubectl get deployment metrics-server -n kube-system

# 로그 확인
kubectl logs -n kube-system deployment/metrics-server
```

#### 2. RBAC 권한 오류
```bash
# ServiceAccount 확인
kubectl get serviceaccount k8s-monitor-service-account -n k8s-monitoring

# ClusterRoleBinding 확인
kubectl get clusterrolebinding k8s-monitor-cluster-role-binding
```

#### 3. Pod 메트릭 수집 실패
```bash
# 애플리케이션 로그 확인
kubectl logs -n k8s-monitoring deployment/k8s-resource-monitor

# 메트릭 API 테스트
kubectl get --raw "/apis/metrics.k8s.io/v1beta1/pods"
```

### 성능 튜닝

#### JVM 메모리 최적화
```bash
# 힙 덤프 생성
kubectl exec -n k8s-monitoring deployment/k8s-resource-monitor -- \
  jcmd 1 GC.run_finalization

# GC 로그 활성화
export JAVA_OPTS="$JAVA_OPTS -XX:+PrintGC -XX:+PrintGCDetails"
```

#### 데이터베이스 최적화
```sql
-- 인덱스 생성
CREATE INDEX idx_resource_timestamp ON resource_metrics(timestamp);
CREATE INDEX idx_resource_type_name ON resource_metrics(resource_type, resource_name);

-- 오래된 데이터 정리
DELETE FROM resource_metrics WHERE timestamp < NOW() - INTERVAL '7 days';
```