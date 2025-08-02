# K8s GPU Management 설치 가이드

## 개요

이 문서는 Kubernetes GPU 관리 시스템의 설치 과정을 단계별로 안내합니다.

## 시스템 요구사항

### 하드웨어 요구사항
- **CPU**: 최소 4 코어, 권장 8 코어
- **메모리**: 최소 8GB RAM, 권장 16GB RAM
- **스토리지**: 최소 50GB, 권장 100GB SSD
- **네트워크**: 1Gbps 이상

### 소프트웨어 요구사항
- **Kubernetes**: 1.24 이상
- **Java**: OpenJDK 17 이상
- **Docker**: 20.10 이상
- **NVIDIA Driver**: 470 이상
- **NVIDIA Container Toolkit**: 최신 버전
- **Oracle Database**: 19c 이상 (운영 환경)

### GPU 요구사항
- **지원 GPU**: NVIDIA Tesla, RTX, A100, H100 시리즈
- **CUDA**: 11.0 이상
- **MIG 지원**: A100, H100 (선택사항)

## 사전 준비

### 1. Kubernetes 클러스터 설정

```bash
# kubectl 설치 확인
kubectl version --client

# 클러스터 연결 확인
kubectl cluster-info

# GPU 지원 노드 확인
kubectl get nodes -l nvidia.com/gpu=true
```

### 2. NVIDIA 드라이버 설치

```bash
# Ubuntu/Debian
sudo apt update
sudo apt install nvidia-driver-470

# CentOS/RHEL
sudo yum install nvidia-driver-470

# 설치 확인
nvidia-smi
```

### 3. NVIDIA Container Toolkit 설치

```bash
# Docker 런타임 설정
distribution=$(. /etc/os-release;echo $ID$VERSION_ID)
curl -fsSL https://nvidia.github.io/libnvidia-container/gpgkey | sudo gpg --dearmor -o /usr/share/keyrings/nvidia-container-toolkit-keyring.gpg
curl -s -L https://nvidia.github.io/libnvidia-container/$distribution/libnvidia-container.list | \
    sed 's#deb https://#deb [signed-by=/usr/share/keyrings/nvidia-container-toolkit-keyring.gpg] https://#g' | \
    sudo tee /etc/apt/sources.list.d/nvidia-container-toolkit.list

sudo apt update
sudo apt install nvidia-container-toolkit
sudo nvidia-ctk runtime configure --runtime=docker
sudo systemctl restart docker
```

### 4. NVIDIA Device Plugin 설치

```bash
# Kubernetes에 NVIDIA Device Plugin 배포
kubectl create -f https://raw.githubusercontent.com/NVIDIA/k8s-device-plugin/v0.14.0/nvidia-device-plugin.yml

# 설치 확인
kubectl get pods -n kube-system | grep nvidia-device-plugin
```

## 설치 과정

### 1. 소스 코드 다운로드

```bash
# Git 클론
git clone https://github.com/your-organization/k8s-gpu-management.git
cd k8s-gpu-management

# 브랜치 확인
git checkout main
```

### 2. 환경 변수 설정

```bash
# 환경 변수 파일 생성
cp .env.example .env

# 환경 변수 수정
vim .env
```

**.env 파일 예시:**
```bash
# 데이터베이스 설정
DB_HOST=oracle-db.default.svc.cluster.local
DB_PORT=1521
DB_SERVICE=ORCL
DB_USERNAME=gpu_admin
DB_PASSWORD=secure_password

# Kubernetes 설정
CLUSTER_NAME=gpu-cluster
NAMESPACE=gpu-management

# GPU 관리 설정
GPU_METRICS_INTERVAL=30s
GPU_ALLOCATION_MAX_DURATION=168h
GPU_COST_TRACKING=true

# 로깅 설정
LOG_LEVEL=INFO
SQL_LOG_LEVEL=WARN
```

### 3. 데이터베이스 설정

#### Oracle Database 설치 (운영 환경)

```bash
# Oracle Database 배포
kubectl apply -f k8s/oracle-db.yaml

# 데이터베이스 스키마 생성
kubectl exec -it oracle-db-0 -- sqlplus / as sysdba
```

**스키마 생성 SQL:**
```sql
-- 사용자 생성
CREATE USER gpu_admin IDENTIFIED BY secure_password;
GRANT CONNECT, RESOURCE, DBA TO gpu_admin;
GRANT UNLIMITED TABLESPACE TO gpu_admin;

-- 테이블스페이스 생성 (선택사항)
CREATE TABLESPACE gpu_data 
DATAFILE '/opt/oracle/oradata/ORCL/gpu_data01.dbf' 
SIZE 1G AUTOEXTEND ON;

ALTER USER gpu_admin DEFAULT TABLESPACE gpu_data;
```

#### H2 Database (개발 환경)

개발 환경에서는 H2 인메모리 데이터베이스가 자동으로 설정됩니다.

### 4. 애플리케이션 빌드

```bash
# Maven 빌드
./mvnw clean package -DskipTests

# Docker 이미지 빌드
docker build -t k8s-gpu-management:latest .

# 이미지 푸시 (선택사항)
docker tag k8s-gpu-management:latest your-registry/k8s-gpu-management:latest
docker push your-registry/k8s-gpu-management:latest
```

### 5. Kubernetes 배포

```bash
# 네임스페이스 생성
kubectl apply -f k8s/namespace.yaml

# ConfigMap 및 Secret 생성
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secret.yaml

# RBAC 설정
kubectl apply -f k8s/rbac.yaml
kubectl apply -f k8s/serviceaccount.yaml

# 애플리케이션 배포
kubectl apply -f k8s/deployment.yaml

# 서비스 생성
kubectl apply -f k8s/service.yaml

# Ingress 설정 (선택사항)
kubectl apply -f k8s/ingress.yaml
```

### 6. GPU 메트릭 수집기 배포

```bash
# DaemonSet으로 GPU 메트릭 수집기 배포
kubectl apply -f k8s/daemonset-gpu-collector.yaml

# 배포 확인
kubectl get daemonset -n gpu-management gpu-metrics-collector
```

### 7. 모니터링 스택 설치 (선택사항)

```bash
# Prometheus ServiceMonitor
kubectl apply -f k8s/monitoring/servicemonitor.yaml

# Prometheus Rules
kubectl apply -f k8s/monitoring/prometheusrule.yaml

# Grafana Dashboard 가져오기
kubectl create configmap grafana-gpu-dashboard \
    --from-file=k8s/monitoring/grafana-dashboard.json \
    -n monitoring
```

## 설치 확인

### 1. 애플리케이션 상태 확인

```bash
# Pod 상태 확인
kubectl get pods -n gpu-management

# 로그 확인
kubectl logs -f deployment/k8s-gpu-management -n gpu-management

# 서비스 확인
kubectl get svc -n gpu-management
```

### 2. GPU 장비 등록 확인

```bash
# GPU 장비 목록 조회
curl -X GET http://your-service-url/api/v1/gpu/devices

# 클러스터 개요 확인
curl -X GET http://your-service-url/api/v1/gpu/overview
```

### 3. 웹 대시보드 접속

```bash
# 포트 포워딩
kubectl port-forward svc/k8s-gpu-management 8080:8080 -n gpu-management

# 브라우저에서 접속
open http://localhost:8080/k8s-monitor
```

### 4. 헬스 체크

```bash
# 애플리케이션 헬스 체크
curl -X GET http://your-service-url/actuator/health

# GPU 클러스터 헬스 체크
curl -X GET http://your-service-url/api/v1/gpu/health
```

## 초기 설정

### 1. GPU 모델 등록

```bash
# A100 모델 등록 예시
curl -X POST http://your-service-url/api/v1/gpu/models \
  -H "Content-Type: application/json" \
  -d '{
    "modelId": "A100_80GB",
    "modelName": "NVIDIA A100 80GB",
    "architecture": "Ampere",
    "memoryGb": 80,
    "migSupport": "Y",
    "powerConsumptionW": 400
  }'
```

### 2. MIG 프로필 설정

```bash
# MIG 프로필 등록
curl -X POST http://your-service-url/api/v1/gpu/mig/profiles \
  -H "Content-Type: application/json" \
  -d '{
    "profileId": "1g.10gb",
    "modelId": "A100_80GB",
    "profileName": "1g.10gb",
    "computeSlices": 1,
    "memorySlices": 2,
    "memoryGb": 10,
    "maxInstancesPerGpu": 7
  }'
```

### 3. 비용 설정

```bash
# application-gpu.yml 파일에서 비용 설정
gpu:
  management:
    cost:
      default-rates:
        A100_80GB: 6.0
        H100_80GB: 8.0
        RTX4090: 2.0
```

## 문제 해결

### 일반적인 문제

1. **Pod가 시작되지 않는 경우**
   ```bash
   kubectl describe pod <pod-name> -n gpu-management
   kubectl logs <pod-name> -n gpu-management
   ```

2. **데이터베이스 연결 실패**
   ```bash
   # 연결 테스트
   kubectl exec -it <pod-name> -n gpu-management -- nc -zv oracle-db 1521
   ```

3. **GPU 장비가 감지되지 않는 경우**
   ```bash
   # NVIDIA Device Plugin 확인
   kubectl get pods -n kube-system | grep nvidia
   
   # 노드의 GPU 라벨 확인
   kubectl get nodes -o json | jq '.items[].status.capacity'
   ```

### 로그 레벨 조정

```yaml
# application.yml
logging:
  level:
    com.k8s.monitor: DEBUG
    org.hibernate.SQL: DEBUG
```

## 업그레이드

### 1. 백업

```bash
# 데이터베이스 백업
./scripts/backup-db.sh

# 설정 파일 백업
kubectl get configmap gpu-management-config -o yaml > backup-config.yaml
```

### 2. 애플리케이션 업데이트

```bash
# 새 이미지로 업데이트
kubectl set image deployment/k8s-gpu-management \
    app=k8s-gpu-management:v1.1.0 -n gpu-management

# 롤아웃 상태 확인
kubectl rollout status deployment/k8s-gpu-management -n gpu-management
```

## 보안 설정

### 1. RBAC 최소 권한 원칙

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: gpu-management-reader
rules:
- apiGroups: [""]
  resources: ["nodes", "pods"]
  verbs: ["get", "list", "watch"]
- apiGroups: ["metrics.k8s.io"]
  resources: ["nodes", "pods"]
  verbs: ["get", "list"]
```

### 2. TLS 설정

```bash
# 인증서 생성
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
    -keyout tls.key -out tls.crt -subj "/CN=gpu-management"

# Secret 생성
kubectl create secret tls gpu-management-tls \
    --cert=tls.crt --key=tls.key -n gpu-management
```

## 고가용성 설정

### 1. 다중 인스턴스 배포

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: k8s-gpu-management
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 1
      maxSurge: 1
```

### 2. 데이터베이스 고가용성

```bash
# Oracle RAC 또는 Master-Slave 구성
# 또는 클라우드 관리형 데이터베이스 사용
```

## 성능 튜닝

### 1. JVM 튜닝

```yaml
env:
- name: JAVA_OPTS
  value: "-Xms2g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

### 2. 데이터베이스 연결 풀 튜닝

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 10
      connection-timeout: 20000
      idle-timeout: 300000
```

## 다음 단계

1. [API 문서](api.md) 검토
2. [설정 가이드](configuration.md) 참조
3. [통합 가이드](integration-guide.md) 확인
4. [문제 해결 가이드](troubleshooting.md) 숙지

## 지원

- **문서**: [GitHub Wiki](https://github.com/your-org/k8s-gpu-management/wiki)
- **이슈 리포트**: [GitHub Issues](https://github.com/your-org/k8s-gpu-management/issues)
- **커뮤니티**: [Slack Channel](https://your-org.slack.com/channels/gpu-management)
