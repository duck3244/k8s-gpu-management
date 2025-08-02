# K8s GPU Management 문제 해결 가이드

## 개요

이 문서는 Kubernetes GPU 관리 시스템에서 발생할 수 있는 일반적인 문제들과 해결 방법을 제공합니다.

## 1. 애플리케이션 시작 문제

### 1.1 Pod가 시작되지 않는 경우

#### 증상
```bash
kubectl get pods -n gpu-management
NAME                                   READY   STATUS    RESTARTS   AGE
k8s-gpu-management-7d8f9b8b8-abc12    0/1     Pending   0          5m
```

#### 진단 방법
```bash
# 사용 가능한 GPU 확인
curl -s http://localhost:8080/k8s-monitor/api/v1/gpu/devices?status=available

# 활성 할당 확인
curl -s http://localhost:8080/k8s-monitor/api/v1/gpu/allocations

# 클러스터 개요 확인
curl -s http://localhost:8080/k8s-monitor/api/v1/gpu/overview
```

#### 해결 방법

**1. GPU 장비 상태 확인**
```bash
# 장애 상태의 GPU 확인
curl -s http://localhost:8080/k8s-monitor/api/v1/gpu/devices | jq '.data[] | select(.deviceStatus != "ACTIVE")'

# GPU 상태 복구
curl -X PUT "http://localhost:8080/k8s-monitor/api/v1/gpu/devices/gpu-worker-1-GPU-00/status?status=ACTIVE"
```

**2. 만료된 할당 정리**
```bash
# 만료된 할당 확인
curl -s http://localhost:8080/k8s-monitor/api/v1/gpu/allocations/expiring?hours=0

# 수동으로 할당 해제
curl -X DELETE http://localhost:8080/k8s-monitor/api/v1/gpu/allocations/ALLOC-ABC12345
```

### 4.2 MIG 인스턴스 생성 실패

#### 증상
```json
{
  "status": "error",
  "error": {
    "code": "MIG_NOT_SUPPORTED",
    "message": "GPU model does not support MIG"
  }
}
```

#### 해결 방법

**1. MIG 지원 GPU 확인**
```bash
# MIG 지원 모델 확인
curl -s http://localhost:8080/k8s-monitor/api/v1/gpu/devices | \
  jq '.data[] | select(.migSupport == true)'

# A100/H100 GPU만 MIG 지원
```

**2. GPU를 MIG 모드로 활성화**
```bash
# 물리적으로 MIG 활성화 (노드에서 실행)
sudo nvidia-smi -mig 1 -i 0

# 애플리케이션에서 MIG 인스턴스 생성
curl -X POST http://localhost:8080/k8s-monitor/api/v1/gpu/mig/devices/gpu-worker-1-GPU-00 \
  -H "Content-Type: application/json" \
  -d '{"profileIds": ["1g.10gb", "2g.20gb"]}'
```

## 5. 네트워크 및 연결 문제

### 5.1 서비스 접근 불가

#### 증상
```bash
curl: (7) Failed to connect to localhost:8080: Connection refused
```

#### 진단 방법
```bash
# 서비스 상태 확인
kubectl get svc k8s-gpu-management -n gpu-management

# 엔드포인트 확인
kubectl get endpoints k8s-gpu-management -n gpu-management

# Pod IP와 포트 확인
kubectl get pods -o wide -n gpu-management
```

#### 해결 방법

**1. 포트 포워딩으로 직접 접근**
```bash
kubectl port-forward deployment/k8s-gpu-management 8080:8080 -n gpu-management
```

**2. 서비스 설정 확인**
```yaml
# 서비스 설정 검토
apiVersion: v1
kind: Service
metadata:
  name: k8s-gpu-management
spec:
  selector:
    app: k8s-gpu-management  # Pod 라벨과 일치 확인
  ports:
  - port: 8080
    targetPort: 8080
    protocol: TCP
```

### 5.2 Ingress 접근 문제

#### 증상
```
502 Bad Gateway
```

#### 해결 방법

**1. Ingress 설정 확인**
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: gpu-management-ingress
spec:
  rules:
  - host: gpu-management.company.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: k8s-gpu-management
            port:
              number: 8080
```

**2. TLS 인증서 문제**
```bash
# 인증서 확인
kubectl get secret gpu-management-tls -n gpu-management -o yaml

# Let's Encrypt 인증서 갱신
kubectl delete certificate gpu-management-cert
kubectl apply -f k8s/certificate.yaml
```

## 6. 성능 문제

### 6.1 응답 시간 지연

#### 증상
```
API 응답이 5초 이상 걸림
```

#### 진단 방법
```bash
# JVM 메트릭 확인
curl -s http://localhost:8080/k8s-monitor/actuator/metrics/jvm.memory.used

# 데이터베이스 연결 풀 상태
curl -s http://localhost:8080/k8s-monitor/actuator/metrics/hikaricp.connections.active

# 애플리케이션 메트릭
curl -s http://localhost:8080/k8s-monitor/actuator/prometheus | grep http_server_requests
```

#### 해결 방법

**1. JVM 메모리 튜닝**
```yaml
env:
- name: JAVA_OPTS
  value: |
    -Xms4g -Xmx8g
    -XX:+UseG1GC
    -XX:MaxGCPauseMillis=100
    -XX:+PrintGCDetails
    -XX:+PrintGCTimeStamps
```

**2. 데이터베이스 쿼리 최적화**
```sql
-- 인덱스 추가
CREATE INDEX idx_gpu_metrics_device_time 
ON gpu_usage_metrics(device_id, timestamp);

CREATE INDEX idx_allocations_status_time 
ON gpu_allocations(status, allocation_time);
```

**3. 캐시 활용**
```yaml
spring:
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=1000,expireAfterWrite=10m
```

### 6.2 메모리 사용량 과다

#### 증상
```
OutOfMemoryError in Pod logs
```

#### 해결 방법

**1. 메모리 제한 증가**
```yaml
resources:
  limits:
    memory: "8Gi"  # 4Gi에서 8Gi로 증가
  requests:
    memory: "4Gi"  # 2Gi에서 4Gi로 증가
```

**2. 메모리 리크 분석**
```bash
# Heap dump 생성
kubectl exec deployment/k8s-gpu-management -n gpu-management -- \
  jcmd 1 GC.run_finalization

# 메모리 사용량 모니터링
watch -n 5 'kubectl top pod -n gpu-management'
```

## 7. 보안 문제

### 7.1 RBAC 권한 부족

#### 증상
```
Forbidden: User "system:serviceaccount:gpu-management:gpu-management" cannot get resource "nodes"
```

#### 해결 방법

**1. 권한 확인**
```bash
# 현재 권한 확인
kubectl auth can-i get nodes --as=system:serviceaccount:gpu-management:gpu-management

# 필요한 권한 추가
kubectl apply -f - <<EOF
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: gpu-management-extended
rules:
- apiGroups: [""]
  resources: ["nodes", "pods"]
  verbs: ["get", "list", "watch"]
- apiGroups: ["metrics.k8s.io"]
  resources: ["nodes", "pods"]
  verbs: ["get", "list"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: gpu-management-extended-binding
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: gpu-management-extended
subjects:
- kind: ServiceAccount
  name: gpu-management
  namespace: gpu-management
EOF
```

### 7.2 Secret 접근 실패

#### 증상
```
Could not resolve placeholder 'DB_PASSWORD' in value "${DB_PASSWORD}"
```

#### 해결 방법

**1. Secret 생성 확인**
```bash
# Secret 존재 확인
kubectl get secret gpu-management-secrets -n gpu-management

# Secret 내용 확인 (Base64 디코딩)
kubectl get secret gpu-management-secrets -o jsonpath='{.data.db-password}' | base64 -d
```

**2. Pod에서 Secret 마운트 확인**
```yaml
# Deployment에 환경 변수 추가
env:
- name: DB_PASSWORD
  valueFrom:
    secretKeyRef:
      name: gpu-management-secrets
      key: db-password
```

## 8. 모니터링 및 알람 문제

### 8.1 Prometheus 메트릭 수집 실패

#### 증상
```
Prometheus에서 GPU 메트릭이 보이지 않음
```

#### 해결 방법

**1. 메트릭 엔드포인트 확인**
```bash
# Actuator 메트릭 확인
curl -s http://localhost:8080/k8s-monitor/actuator/prometheus | grep gpu

# ServiceMonitor 설정 확인
kubectl get servicemonitor gpu-management-monitor -n gpu-management -o yaml
```

**2. 네트워크 정책 확인**
```bash
# Prometheus가 애플리케이션에 접근 가능한지 확인
kubectl get networkpolicy -n gpu-management
```

### 8.2 Slack 알림이 오지 않는 경우

#### 해결 방법

**1. 웹훅 URL 테스트**
```bash
# 수동으로 Slack 메시지 발송 테스트
curl -X POST $SLACK_WEBHOOK_URL \
  -H 'Content-Type: application/json' \
  -d '{"text": "GPU Management Test Message"}'
```

**2. 애플리케이션 알림 설정 확인**
```yaml
gpu:
  management:
    alerts:
      enabled: true
      notification:
        slack:
          enabled: true
          webhook-url: "${SLACK_WEBHOOK_URL}"
```

## 9. 데이터 정합성 문제

### 9.1 GPU 할당 정보 불일치

#### 증상
```
GPU가 "사용 중"으로 표시되지만 실제로는 사용되지 않음
```

#### 해결 방법

**1. 할당 정보 정리**
```bash
# 만료된 할당 확인
curl -s http://localhost:8080/k8s-monitor/api/v1/gpu/allocations | \
  jq '.data[] | select(.status == "ALLOCATED" and .plannedReleaseTime < now)'

# 수동으로 정리
curl -X POST http://localhost:8080/k8s-monitor/api/v1/gpu/allocations/cleanup
```

**2. 데이터베이스 정합성 검사**
```sql
-- 고아 할당 확인
SELECT a.allocation_id, a.allocated_resource 
FROM gpu_allocations a 
LEFT JOIN gpu_devices d ON a.allocated_resource = d.device_id 
WHERE d.device_id IS NULL AND a.status = 'ALLOCATED';

-- MIG 인스턴스 정합성 확인
SELECT m.mig_id, m.allocated 
FROM mig_instances m 
WHERE m.allocated = 'Y' 
AND NOT EXISTS (
  SELECT 1 FROM gpu_allocations a 
  WHERE a.allocated_resource = m.mig_id 
  AND a.status = 'ALLOCATED'
);
```

## 10. 백업 및 복구 문제

### 10.1 데이터베이스 백업 실패

#### 해결 방법

**1. 백업 스크립트 확인**
```bash
# 백업 CronJob 상태 확인
kubectl get cronjob gpu-db-backup -n gpu-management

# 최근 백업 작업 확인
kubectl get jobs -n gpu-management | grep backup
```

**2. 수동 백업 실행**
```bash
kubectl create job manual-backup --from=cronjob/gpu-db-backup -n gpu-management
```

### 10.2 복구 절차

**1. 데이터베이스 복구**
```bash
# 백업 파일 확인
kubectl exec -it oracle-db-0 -- ls -la /backup/

# 복구 실행
kubectl exec -it oracle-db-0 -- \
  impdp gpu_admin/password@localhost:1521/ORCL \
  DIRECTORY=backup_dir \
  DUMPFILE=gpu_backup_20250803_020000.dmp \
  SCHEMAS=gpu_admin
```

## 11. 일반적인 진단 명령어

### 11.1 시스템 상태 확인

```bash
# 전체 Pod 상태
kubectl get pods -n gpu-management -o wide

# 리소스 사용량
kubectl top pods -n gpu-management
kubectl top nodes

# 이벤트 확인
kubectl get events -n gpu-management --sort-by='.lastTimestamp'

# 서비스 상태
kubectl get svc,ep -n gpu-management
```

### 11.2 애플리케이션 상태 확인

```bash
# 헬스 체크
curl -s http://localhost:8080/k8s-monitor/actuator/health | jq

# 메트릭 확인
curl -s http://localhost:8080/k8s-monitor/actuator/metrics

# 환경 정보
curl -s http://localhost:8080/k8s-monitor/actuator/env

# 설정 정보
curl -s http://localhost:8080/k8s-monitor/actuator/configprops
```

### 11.3 GPU 시스템 확인

```bash
# GPU 장비 상태
curl -s http://localhost:8080/k8s-monitor/api/v1/gpu/devices | jq '.data[] | {deviceId, deviceStatus, currentTempC}'

# 클러스터 개요
curl -s http://localhost:8080/k8s-monitor/api/v1/gpu/overview | jq

# 활성 할당
curl -s http://localhost:8080/k8s-monitor/api/v1/gpu/allocations | jq '.data[] | {allocationId, status, allocationTime}'
```

## 12. 로그 분석

### 12.1 중요한 로그 패턴

```bash
# GPU 관련 오류
kubectl logs deployment/k8s-gpu-management -n gpu-management | grep -i "gpu\|allocation\|device"

# 데이터베이스 관련 오류
kubectl logs deployment/k8s-gpu-management -n gpu-management | grep -i "sql\|hibernate\|oracle"

# 성능 관련 경고
kubectl logs deployment/k8s-gpu-management -n gpu-management | grep -i "timeout\|slow\|performance"
```

### 12.2 로그 레벨 조정

```bash
# 특정 패키지의 로그 레벨 변경
curl -X POST http://localhost:8080/k8s-monitor/actuator/loggers/com.k8s.monitor.service.gpu \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'

# 모든 GPU 관련 로그를 DEBUG로 변경
curl -X POST http://localhost:8080/k8s-monitor/actuator/loggers/com.k8s.monitor.gpu \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'
```

## 13. 긴급 복구 절차

### 13.1 서비스 중단 시 긴급 복구

```bash
# 1. Pod 재시작
kubectl rollout restart deployment/k8s-gpu-management -n gpu-management

# 2. 데이터베이스 연결 확인
kubectl exec -it deployment/k8s-gpu-management -n gpu-management -- \
  nc -zv oracle-db.default.svc.cluster.local 1521

# 3. 이전 버전으로 롤백 (필요시)
kubectl rollout undo deployment/k8s-gpu-management -n gpu-management

# 4. 수동으로 GPU 할당 정리
curl -X POST http://localhost:8080/k8s-monitor/api/v1/gpu/allocations/cleanup
```

### 13.2 데이터 손실 시 복구

```bash
# 1. 최신 백업 확인
kubectl get jobs -n gpu-management | grep backup | tail -5

# 2. 백업에서 복구
kubectl exec -it oracle-db-0 -- \
  impdp gpu_admin/password DIRECTORY=backup_dir DUMPFILE=latest_backup.dmp

# 3. 애플리케이션 재시작
kubectl rollout restart deployment/k8s-gpu-management -n gpu-management
```

## 14. 지원 및 에스컬레이션

### 14.1 로그 수집 스크립트

```bash
#!/bin/bash
# support-bundle.sh

NAMESPACE="gpu-management"
OUTPUT_DIR="support-bundle-$(date +%Y%m%d_%H%M%S)"

mkdir -p "$OUTPUT_DIR"

# 기본 정보 수집
kubectl get pods -n $NAMESPACE -o wide > "$OUTPUT_DIR/pods.txt"
kubectl get svc -n $NAMESPACE -o wide > "$OUTPUT_DIR/services.txt"
kubectl get events -n $NAMESPACE --sort-by='.lastTimestamp' > "$OUTPUT_DIR/events.txt"

# 로그 수집
kubectl logs deployment/k8s-gpu-management -n $NAMESPACE --tail=1000 > "$OUTPUT_DIR/app-logs.txt"

# 설정 정보 수집
kubectl get configmap gpu-management-config -n $NAMESPACE -o yaml > "$OUTPUT_DIR/configmap.yaml"

# 시스템 상태
kubectl top pods -n $NAMESPACE > "$OUTPUT_DIR/resource-usage.txt"
kubectl top nodes > "$OUTPUT_DIR/node-usage.txt"

echo "Support bundle created: $OUTPUT_DIR"
tar -czf "$OUTPUT_DIR.tar.gz" "$OUTPUT_DIR"
```

### 14.2 지원 연락처

- **기술 지원**: support@company.com
- **긴급 연락처**: +82-10-xxxx-xxxx
- **GitHub Issues**: https://github.com/your-org/k8s-gpu-management/issues
- **Slack**: #gpu-management-support

이 문제 해결 가이드를 통해 대부분의 일반적인 문제들을 해결할 수 있습니다. 문제가 지속되는 경우 지원팀에 연락하여 도움을 받으시기 바랍니다. Pod 상세 정보 확인
kubectl describe pod k8s-gpu-management-7d8f9b8b8-abc12 -n gpu-management

# 이벤트 확인
kubectl get events -n gpu-management --sort-by='.lastTimestamp'

# 노드 리소스 확인
kubectl top nodes
kubectl describe nodes
```

#### 일반적인 원인 및 해결책

**1. 리소스 부족**
```yaml
# 해결책: 리소스 요청량 조정
resources:
  requests:
    memory: "1Gi"    # 2Gi에서 1Gi로 감소
    cpu: "250m"      # 500m에서 250m로 감소
  limits:
    memory: "2Gi"
    cpu: "1000m"
```

**2. 이미지 Pull 실패**
```bash
# 이미지 확인
docker pull k8s-gpu-management:latest

# Private Registry 인증
kubectl create secret docker-registry regcred \
  --docker-server=your-registry.com \
  --docker-username=your-username \
  --docker-password=your-password \
  --docker-email=your-email@company.com
```

**3. ConfigMap/Secret 누락**
```bash
# ConfigMap 생성 확인
kubectl get configmap gpu-management-config -n gpu-management

# Secret 생성 확인
kubectl get secret gpu-management-secrets -n gpu-management

# 누락된 경우 생성
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secret.yaml
```

### 1.2 Pod는 실행되지만 Ready 상태가 아닌 경우

#### 진단 방법
```bash
# Pod 로그 확인
kubectl logs k8s-gpu-management-7d8f9b8b8-abc12 -n gpu-management

# 헬스 체크 엔드포인트 직접 호출
kubectl port-forward pod/k8s-gpu-management-7d8f9b8b8-abc12 8080:8080 -n gpu-management
curl http://localhost:8080/k8s-monitor/actuator/health
```

#### 일반적인 원인 및 해결책

**1. 데이터베이스 연결 실패**
```bash
# 데이터베이스 연결 테스트
kubectl exec -it k8s-gpu-management-7d8f9b8b8-abc12 -n gpu-management -- \
  nc -zv oracle-db.default.svc.cluster.local 1521

# Oracle DB 상태 확인
kubectl get pods -l app=oracle-db
kubectl logs oracle-db-0
```

**해결책:**
```yaml
# application.yml에서 DB 설정 확인
spring:
  datasource:
    url: jdbc:oracle:thin:@oracle-db.default.svc.cluster.local:1521:ORCL
    username: gpu_admin
    password: ${DB_PASSWORD}
    # 연결 시도 시간 증가
    hikari:
      connection-timeout: 60000
      validation-timeout: 10000
```

**2. Kubernetes API 접근 권한 부족**
```bash
# ServiceAccount 확인
kubectl get serviceaccount gpu-management -n gpu-management

# ClusterRoleBinding 확인
kubectl get clusterrolebinding gpu-management-binding

# 권한 테스트
kubectl auth can-i get pods --as=system:serviceaccount:gpu-management:gpu-management
```

## 2. 데이터베이스 관련 문제

### 2.1 Oracle Database 연결 문제

#### 증상
```
2025-08-03 12:00:00 ERROR o.h.e.j.s.SqlExceptionHelper - ORA-12514: TNS:listener does not currently know of service requested in connect descriptor
```

#### 해결 방법

**1. 연결 문자열 확인**
```bash
# Oracle DB 서비스 확인
kubectl get svc oracle-db -o wide

# TNS 연결 테스트
kubectl run oracle-test --rm -it --image=oracle/instantclient:19.3.0 -- \
  sqlplus gpu_admin/password@oracle-db.default.svc.cluster.local:1521/ORCL
```

**2. 방화벽/네트워크 정책 확인**
```bash
# 네트워크 정책 확인
kubectl get networkpolicy -n gpu-management

# 포트 접근 테스트
kubectl exec -it k8s-gpu-management-pod -n gpu-management -- \
  telnet oracle-db.default.svc.cluster.local 1521
```

### 2.2 데이터베이스 성능 문제

#### 증상
```
2025-08-03 12:00:00 WARN  c.z.h.p.HikariPool - HikariPool-1 - Thread starvation or clock leap detected
```

#### 해결 방법

**1. 연결 풀 튜닝**
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20      # 기본값에서 증가
      minimum-idle: 10
      connection-timeout: 30000  # 30초로 증가
      idle-timeout: 600000       # 10분으로 증가
      max-lifetime: 1800000      # 30분으로 증가
```

**2. 쿼리 최적화**
```sql
-- 느린 쿼리 확인
SELECT sql_text, elapsed_time, executions 
FROM v$sql 
WHERE elapsed_time > 1000000 
ORDER BY elapsed_time DESC;

-- 인덱스 확인
SELECT table_name, index_name, column_name 
FROM user_ind_columns 
WHERE table_name = 'GPU_USAGE_METRICS';
```

## 3. GPU 메트릭 수집 문제

### 3.1 nvidia-smi 명령어 실패

#### 증상
```
2025-08-03 12:00:00 ERROR c.k.m.s.g.GpuMetricsCollectionService - Error executing nvidia-smi: No such file or directory
```

#### 해결 방법

**1. NVIDIA 드라이버 설치 확인**
```bash
# 노드에서 nvidia-smi 실행 확인
kubectl debug node/gpu-worker-1 -it --image=nvidia/cuda:11.0-base
nvidia-smi
```

**2. DaemonSet으로 GPU 메트릭 수집기 배포**
```yaml
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: gpu-metrics-collector
  namespace: gpu-management
spec:
  selector:
    matchLabels:
      app: gpu-metrics-collector
  template:
    metadata:
      labels:
        app: gpu-metrics-collector
    spec:
      hostPID: true
      containers:
      - name: collector
        image: nvidia/cuda:11.0-base
        command: ["/bin/bash", "-c"]
        args:
        - |
          while true; do
            nvidia-smi --query-gpu=index,name,temperature.gpu,utilization.gpu,memory.used,memory.total --format=csv,noheader,nounits > /shared/gpu-metrics.csv
            sleep 30
          done
        volumeMounts:
        - name: shared-data
          mountPath: /shared
        securityContext:
          privileged: true
      volumes:
      - name: shared-data
        hostPath:
          path: /tmp/gpu-metrics
```

### 3.2 메트릭 데이터가 수집되지 않는 경우

#### 진단 방법
```bash
# 메트릭 수집 상태 확인
curl -s http://localhost:8080/k8s-monitor/api/v1/gpu/metrics/collection-status

# 수집된 메트릭 확인
curl -s http://localhost:8080/k8s-monitor/api/v1/gpu/metrics/usage-statistics?hours=1

# 수집기 로그 확인
kubectl logs daemonset/gpu-metrics-collector -n gpu-management
```

#### 해결 방법

**1. 수집 간격 조정**
```yaml
gpu:
  management:
    metrics:
      collection-interval: 60s  # 30s에서 60s로 증가
      batch-size: 50           # 100에서 50으로 감소
```

**2. 수동 메트릭 수집 트리거**
```bash
curl -X POST http://localhost:8080/k8s-monitor/api/v1/gpu/metrics/collect
```

## 4. GPU 할당 문제

### 4.1 GPU 할당 실패

#### 증상
```json
{
  "status": "error",
  "error": {
    "code": "GPU_NOT_AVAILABLE",
    "message": "No suitable GPU device available for allocation"
  }
}
```

#### 진단 방법
```bash
#