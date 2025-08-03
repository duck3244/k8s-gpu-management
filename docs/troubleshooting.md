# K8s GPU Management 문제 해결 가이드

이 문서는 Kubernetes GPU 관리 시스템에서 발생할 수 있는 일반적인 문제들과 해결 방법을 제공합니다.

## 📋 목차
- [GPU 장비 관련 문제](#gpu-장비-관련-문제)
- [MIG 관리 문제](#mig-관리-문제)
- [할당 및 스케줄링 문제](#할당-및-스케줄링-문제)
- [메트릭 수집 문제](#메트릭-수집-문제)
- [성능 관련 문제](#성능-관련-문제)
- [네트워킹 문제](#네트워킹-문제)
- [데이터베이스 연결 문제](#데이터베이스-연결-문제)
- [보안 및 권한 문제](#보안-및-권한-문제)

---

## GPU 장비 관련 문제

### 🔥 문제: GPU 과열 (Temperature > 85°C)

#### 증상
- GPU 온도가 85°C 이상으로 지속됨
- 성능 저하 또는 thermal throttling 발생
- 시스템 불안정성

#### 진단 방법
```bash
# GPU 온도 확인
nvidia-smi --query-gpu=temperature.gpu,temperature.memory --format=csv

# 시스템 로그 확인
journalctl -u nvidia-persistenced -f

# GPU 메트릭 조회
curl -s http://localhost:8080/k8s-monitor/api/v1/gpu/devices/{device-id}/health
```

#### 해결 방법
1. **즉시 조치**
   ```bash
   # 워크로드 일시 중단
   kubectl scale deployment gpu-workload --replicas=0
   
   # GPU 전력 제한 설정
   sudo nvidia-smi -pl 300  # 300W로 제한
   ```

2. **근본 원인 해결**
   ```bash
   # 팬 속도 확인 및 조정
   nvidia-smi --query-gpu=fan.speed --format=csv
   
   # 냉각 시스템 점검
   sudo sensors
   
   # 데이터센터 환경 온도 확인
   ```

3. **예방 조치**
   - 정기적인 하드웨어 점검
   - 냉각 시스템 유지보수
   - 환경 모니터링 강화

---

### ⚡ 문제: GPU 전력 소모 이상

#### 증상
- 예상보다 높은 전력 소모
- 전력 공급 장치 과부하
- 시스템 재시작 또는 전원 차단

#### 진단 방법
```bash
# 전력 사용량 모니터링
nvidia-smi --query-gpu=power.draw,power.limit --format=csv,noheader,nounits

# 시간별 전력 소모 추세 확인
curl -s "http://localhost:8080/k8s-monitor/api/v1/gpu/metrics/data?hours=24&deviceId={device-id}"

# 전력 공급 장치 상태 확인
sudo dmidecode -t 39
```

#### 해결 방법
1. **즉시 전력 제한**
   ```bash
   # 전력 한도 설정
   sudo nvidia-smi -pl 250  # 250W로 제한
   
   # 클럭 속도 제한
   sudo nvidia-smi -lgc 1200  # Graphics clock을 1200MHz로 제한
   ```

2. **워크로드 분산**
   ```bash
   # 다른 노드로 워크로드 이동
   kubectl drain {node-name} --ignore-daemonsets
   kubectl uncordon {other-node-name}
   ```

---

### 🚫 문제: GPU 인식 불가

#### 증상
- `nvidia-smi` 명령어 실패
- Kubernetes에서 GPU 리소스 인식 안됨
- 드라이버 오류 메시지

#### 진단 방법
```bash
# GPU 하드웨어 인식 확인
lspci | grep -i nvidia

# 드라이버 상태 확인
nvidia-smi
modinfo nvidia

# Kubernetes GPU 플러그인 상태
kubectl get nodes -o yaml | grep nvidia.com/gpu

# 시스템 로그 확인
dmesg | grep -i nvidia
journalctl -u nvidia-persistenced
```

#### 해결 방법
1. **드라이버 재설치**
   ```bash
   # 기존 드라이버 제거
   sudo apt-get purge nvidia-*
   
   # 최신 드라이버 설치
   sudo apt update
   sudo apt install nvidia-driver-535
   
   # 시스템 재시작
   sudo reboot
   ```

2. **NVIDIA Container Toolkit 재설정**
   ```bash
   # Container Toolkit 재설치
   sudo apt-get update
   sudo apt-get install -y nvidia-container-toolkit
   
   # Docker 재시작
   sudo systemctl restart docker
   ```

3. **Kubernetes GPU Operator 재배포**
   ```bash
   # GPU Operator 삭제
   kubectl delete -f https://raw.githubusercontent.com/NVIDIA/gpu-operator/main/deployments/gpu-operator/gpu-operator.yaml
   
   # 재배포
   kubectl apply -f https://raw.githubusercontent.com/NVIDIA/gpu-operator/main/deployments/gpu-operator/gpu-operator.yaml
   ```

---

## MIG 관리 문제

### 🔧 문제: MIG 인스턴스 생성 실패

#### 증상
- MIG 인스턴스 생성 API 호출 실패
- "MIG not supported" 오류 메시지
- GPU가 MIG 모드로 전환되지 않음

#### 진단 방법
```bash
# GPU MIG 지원 여부 확인
nvidia-smi --query-gpu=name,mig.mode.current,mig.mode.pending --format=csv

# MIG 인스턴스 현재 상태
nvidia-smi mig -lgip

# 지원되는 MIG 프로필 확인
nvidia-smi mig -lgip -i 0

# 애플리케이션 로그 확인
kubectl logs -f deployment/k8s-gpu-monitor | grep -i mig
```

#### 해결 방법
1. **MIG 모드 활성화**
   ```bash
   # MIG 모드 활성화
   sudo nvidia-smi -mig 1
   
   # 시스템 재시작 (필요한 경우)
   sudo reboot
   
   # MIG 모드 확인
   nvidia-smi --query-gpu=mig.mode.current --format=csv
   ```

2. **GPU 모델 호환성 확인**
   ```bash
   # MIG 지원 GPU 확인 (A100, A30, H100 등)
   nvidia-smi --query-gpu=name --format=csv
   
   # 드라이버 버전 확인 (450.80.02 이상 필요)
   nvidia-smi --query-gpu=driver_version --format=csv
   ```

3. **권한 문제 해결**
   ```bash
   # nvidia-ml 권한 확인
   ls -la /dev/nvidia*
   
   # 필요시 권한 수정
   sudo chmod 666 /dev/nvidia*
   ```

---

### 🎯 문제: MIG 인스턴스 할당 실패

#### 증상
- MIG 인스턴스가 있지만 할당되지 않음
- "No suitable MIG instance available" 오류
- Pod가 Pending 상태로 남아있음

#### 진단 방법
```bash
# 사용 가능한 MIG 인스턴스 확인
curl -s http://localhost:8080/k8s-monitor/api/v1/gpu/mig/available

# MIG 인스턴스 세부 정보
nvidia-smi mig -lgi

# Pod 이벤트 확인
kubectl describe pod {pod-name}

# GPU 할당 요청 확인
curl -s http://localhost:8080/k8s-monitor/api/v1/gpu/allocations
```

#### 해결 방법
1. **MIG 인스턴스 상태 복구**
   ```bash
   # 모든 MIG 인스턴스 삭제 후 재생성
   sudo nvidia-smi mig -dci
   sudo nvidia-smi mig -dgi
   
   # 새로운 MIG 인스턴스 생성
   sudo nvidia-smi mig -cgi 19,14,9  # 1g.10gb, 2g.20gb, 3g.40gb 프로필
   sudo nvidia-smi mig -cci -gi 0
   ```

2. **할당 요청 요구사항 확인**
   ```bash
   # 요청된 메모리 크기와 사용 가능한 MIG 프로필 비교
   curl -s http://localhost:8080/k8s-monitor/api/v1/gpu/mig/profiles | \
     jq '.data[] | {profileName, memoryGb, maxInstancesPerGpu}'
   ```

3. **수동으로 적절한 MIG 인스턴스 할당**
   ```bash
   # 특정 프로필의 MIG 인스턴스 생성
   curl -X POST http://localhost:8080/k8s-monitor/api/v1/gpu/mig/devices/gpu-worker-1-GPU-00 \
     -H "Content-Type: application/json" \
     -d '{"profileIds": ["1g.10gb"]}'
   ```

---

## 할당 및 스케줄링 문제

### 💼 문제: GPU 할당 대기열 증가

#### 증상
- 할당 요청이 대기열에서 처리되지 않음
- "No available GPU" 메시지 지속
- Pod가 Pending 상태로 유지됨

#### 진단 방법
```bash
# 대기 중인 할당 요청 확인
curl -s http://localhost:8080/k8s-monitor/api/v1/gpu/allocations | \
  jq '.data[] | select(.status == "PENDING")'

# 사용 가능한 GPU 리소스 확인
curl -s http://localhost:8080/k8s-monitor/api/v1/gpu/devices?status=available

# 클러스터 용량 분석
curl -s http://localhost:8080/k8s-monitor/api/v1/gpu/overview
```

#### 해결 방법
1. **만료된 할당 정리**
   ```bash
   # 만료된 할당 자동 정리
   curl -X POST http://localhost:8080/k8s-monitor/api/v1/gpu/allocations/cleanup
   
   # 특정 할당 수동 해제
   curl -X DELETE http://localhost:8080/k8s-monitor/api/v1/gpu/allocations/ALLOC-ABC12345
   ```

2. **우선순위 기반 스케줄링**
   ```bash
   # 높은 우선순위 할당 생성
   curl -X POST http://localhost:8080/k8s-monitor/api/v1/gpu/allocations \
     -H "Content-Type: application/json" \
     -d '{
       "namespace": "critical-workload",
       "podName": "urgent-task",
       "priorityClass": "high",
       "workloadType": "Training"
     }'
   ```

---

### 🕐 문제: 할당 시간 초과

#### 증상
- 할당이 계획된 시간을 초과하여 지속됨
- 자동 해제가 작동하지 않음
- 리소스가 반환되지 않음

#### 진단 방법
```bash
# 만료 예정 할당 확인
curl -s http://localhost:8080/k8s-monitor/api/v1/gpu/allocations/expiring?hours=0

# 장기 실행 할당 확인
curl -s http://localhost:8080/k8s-monitor/api/v1/gpu/allocations | \
  jq '.data[] | select(.status == "ALLOCATED" and (.allocationTime | fromdateiso8601) < (now - 86400))'
```

#### 해결 방법
1. **자동 만료 메커니즘 확인**
   ```bash
   # 스케줄러 상태 확인
   kubectl logs deployment/k8s-gpu-management -n gpu-management | grep "autoExpireAllocations"
   
   # 수동으로 만료 작업 실행
   curl -X POST http://localhost:8080/k8s-monitor/api/v1/gpu/allocations/expire-old
   ```

2. **할당 연장**
   ```bash
   # 할당 시간 연장
   curl -X PUT http://localhost:8080/k8s-monitor/api/v1/gpu/allocations/ALLOC-ABC12345/extend \
     -H "Content-Type: application/json" \
     -d '{"additionalHours": 24}'
   ```

---

## 메트릭 수집 문제

### 📊 문제: 메트릭 데이터 수집 중단

#### 증상
- Prometheus에서 GPU 메트릭이 사라짐
- 그라파나 대시보드에 데이터 없음
- "No data" 메시지

#### 진단 방법
```bash
# 메트릭 수집 상태 확인
curl -s http://localhost:8080/k8s-monitor/api/v1/gpu/metrics/collection-status

# 메트릭 엔드포인트 직접 확인
curl -s http://localhost:8080/k8s-monitor/actuator/prometheus | grep gpu

# DCGM Exporter 상태 확인
kubectl get pods -l app=dcgm-exporter -n gpu-system
kubectl logs daemonset/dcgm-exporter -n gpu-system
```

#### 해결 방법
1. **메트릭 수집기 재시작**
   ```bash
   # GPU 메트릭 수집 서비스 재시작
   kubectl rollout restart deployment/k8s-gpu-management -n gpu-management
   
   # DCGM Exporter 재시작
   kubectl rollout restart daemonset/dcgm-exporter -n gpu-system
   ```

2. **수동 메트릭 수집**
   ```bash
   # 메트릭 수집 강제 실행
   curl -X POST http://localhost:8080/k8s-monitor/api/v1/gpu/metrics/collect
   
   # 컬렉션 재시작
   curl -X POST http://localhost:8080/k8s-monitor/api/v1/gpu/metrics/collection/restart
   ```

---

### 🔍 문제: nvidia-smi 명령어 실행 실패

#### 증상
- "nvidia-smi: command not found" 오류
- GPU 메트릭 수집 실패
- 드라이버 관련 오류

#### 진단 방법
```bash
# 노드에서 NVIDIA 드라이버 확인
kubectl debug node/gpu-worker-1 -it --image=nvidia/cuda:11.8-devel-ubuntu20.04
# 컨테이너 내에서:
nvidia-smi
ls -la /usr/bin/nvidia-smi

# GPU 디바이스 파일 확인
ls -la /dev/nvidia*

# 드라이버 모듈 확인
lsmod | grep nvidia
```

#### 해결 방법
1. **NVIDIA 드라이버 재설치**
   ```bash
   # 기존 드라이버 제거
   sudo apt-get purge nvidia-*
   sudo apt-get autoremove
   
   # 최신 드라이버 설치
   sudo apt update
   sudo apt install nvidia-driver-535
   sudo reboot
   ```

2. **NVIDIA Container Toolkit 재설정**
   ```bash
   # Container Toolkit 재설치
   curl -fsSL https://nvidia.github.io/libnvidia-container/gpgkey | sudo gpg --dearmor -o /usr/share/keyrings/nvidia-container-toolkit-keyring.gpg
   
   echo "deb [signed-by=/usr/share/keyrings/nvidia-container-toolkit-keyring.gpg] https://nvidia.github.io/libnvidia-container/stable/deb/$(ARCH) /" | \
     sudo tee /etc/apt/sources.list.d/nvidia-container-toolkit.list
   
   sudo apt update
   sudo apt install -y nvidia-container-toolkit
   sudo nvidia-ctk runtime configure --runtime=docker
   sudo systemctl restart docker
   ```

---

## 성능 관련 문제

### 🐌 문제: API 응답 시간 지연

#### 증상
- API 호출이 5초 이상 소요
- 타임아웃 오류 발생
- 사용자 인터페이스 응답 지연

#### 진단 방법
```bash
# JVM 메트릭 확인
curl -s http://localhost:8080/k8s-monitor/actuator/metrics/jvm.memory.used
curl -s http://localhost:8080/k8s-monitor/actuator/metrics/jvm.gc.pause

# 데이터베이스 연결 풀 상태
curl -s http://localhost:8080/k8s-monitor/actuator/metrics/hikaricp.connections.active

# HTTP 요청 메트릭
curl -s http://localhost:8080/k8s-monitor/actuator/prometheus | grep http_server_requests
```

#### 해결 방법
1. **JVM 메모리 최적화**
   ```yaml
   # Deployment에서 JVM 옵션 조정
   env:
   - name: JAVA_OPTS
     value: |
       -Xms4g -Xmx8g
       -XX:+UseG1GC
       -XX:MaxGCPauseMillis=200
       -XX:+UseStringDeduplication
       -XX:+OptimizeStringConcat
   ```

2. **데이터베이스 쿼리 최적화**
   ```sql
   -- 자주 사용되는 쿼리에 인덱스 추가
   CREATE INDEX idx_gpu_metrics_device_timestamp 
   ON gpu_usage_metrics(device_id, timestamp);
   
   CREATE INDEX idx_allocations_status_time 
   ON gpu_allocations(status, allocation_time);
   
   -- 통계 정보 업데이트
   EXEC DBMS_STATS.GATHER_SCHEMA_STATS('GPU_ADMIN');
   ```

3. **캐싱 활성화**
   ```yaml
   spring:
     cache:
       type: caffeine
       caffeine:
         spec: maximumSize=1000,expireAfterWrite=10m
   ```

---

### 💾 문제: 메모리 사용량 과다

#### 증상
- OutOfMemoryError 발생
- Pod 재시작 반복
- GC 시간 과다

#### 진단 방법
```bash
# 메모리 사용량 모니터링
kubectl top pod k8s-gpu-management-xxx -n gpu-management

# JVM 메모리 상세 정보
curl -s http://localhost:8080/k8s-monitor/actuator/metrics/jvm.memory.used | jq
curl -s http://localhost:8080/k8s-monitor/actuator/metrics/jvm.memory.max | jq

# GC 메트릭 확인
curl -s http://localhost:8080/k8s-monitor/actuator/metrics/jvm.gc.pause | jq
```

#### 해결 방법
1. **메모리 리소스 증가**
   ```yaml
   resources:
     limits:
       memory: "8Gi"  # 4Gi에서 8Gi로 증가
     requests:
       memory: "4Gi"  # 2Gi에서 4Gi로 증가
   ```

2. **메모리 리크 방지**
   ```java
   // 대량 데이터 처리 시 배치 크기 제한
   @Value("${gpu.management.metrics.batch-size:100}")
   private int batchSize;
   
   // 오래된 메트릭 데이터 정리
   @Scheduled(cron = "0 0 2 * * *")
   public void cleanupOldMetrics() {
       LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
       metricsRepository.deleteOldMetrics(cutoff);
   }
   ```

---

## 네트워킹 문제

### 🌐 문제: 서비스 간 통신 실패

#### 증상
- 503 Service Unavailable 오류
- 연결 거부 (Connection refused)
- DNS 해석 실패

#### 진단 방법
```bash
# 서비스 디스커버리 확인
kubectl get svc,ep -n gpu-management

# DNS 해석 테스트
kubectl exec -it deployment/k8s-gpu-management -n gpu-management -- \
  nslookup oracle-db.default.svc.cluster.local

# 네트워크 연결 테스트
kubectl exec -it deployment/k8s-gpu-management -n gpu-management -- \
  nc -zv oracle-db.default.svc.cluster.local 1521
```

#### 해결 방법
1. **서비스 설정 확인**
   ```yaml
   # 서비스 라벨 셀렉터 검증
   apiVersion: v1
   kind: Service
   metadata:
     name: k8s-gpu-management
   spec:
     selector:
       app: k8s-gpu-management  # Pod 라벨과 정확히 일치해야 함
     ports:
     - port: 8080
       targetPort: 8080
   ```

2. **네트워크 정책 확인**
   ```bash
   # 네트워크 정책이 트래픽을 차단하는지 확인
   kubectl get networkpolicy -n gpu-management
   kubectl describe networkpolicy -n gpu-management
   ```

---

### 🔒 문제: Ingress TLS 인증서 오류

#### 증상
- SSL/TLS 핸드셰이크 실패
- "Certificate not valid" 오류
- HTTPS 접속 불가

#### 진단 방법
```bash
# 인증서 상태 확인
kubectl get certificate -n gpu-management
kubectl describe certificate gpu-management-tls -n gpu-management

# Ingress 설정 확인
kubectl get ingress -n gpu-management -o yaml

# 인증서 만료일 확인
openssl s_client -connect gpu-management.company.com:443 -servername gpu-management.company.com 2>/dev/null | openssl x509 -noout -dates
```

#### 해결 방법
1. **Let's Encrypt 인증서 갱신**
   ```bash
   # cert-manager를 통한 인증서 갱신
   kubectl delete certificate gpu-management-tls -n gpu-management
   kubectl apply -f - <<EOF
   apiVersion: cert-manager.io/v1
   kind: Certificate
   metadata:
     name: gpu-management-tls
     namespace: gpu-management
   spec:
     secretName: gpu-management-tls
     issuerRef:
       name: letsencrypt-prod
       kind: ClusterIssuer
     dnsNames:
     - gpu-management.company.com
   EOF
   ```

---

## 데이터베이스 연결 문제

### 🗄️ 문제: Oracle Database 연결 실패

#### 증상
- "TNS:listener does not currently know of service" 오류
- 연결 타임아웃
- 인증 실패

#### 진단 방법
```bash
# Oracle DB Pod 상태 확인
kubectl get pods -l app=oracle-db
kubectl logs oracle-db-0 --tail=100

# 리스너 상태 확인
kubectl exec -it oracle-db-0 -- lsnrctl status

# TNS 연결 테스트
kubectl exec -it oracle-db-0 -- \
  sqlplus gpu_admin/password@localhost:1521/ORCL
```

#### 해결 방법
1. **Oracle 서비스 재시작**
   ```bash
   # Oracle DB 재시작
   kubectl delete pod oracle-db-0
   kubectl wait --for=condition=Ready pod/oracle-db-0 --timeout=300s
   ```

2. **연결 문자열 수정**
   ```yaml
   spring:
     datasource:
       url: jdbc:oracle:thin:@oracle-db.default.svc.cluster.local:1521:ORCL
       # 또는 서비스 이름 형식으로:
       # url: jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=oracle-db.default.svc.cluster.local)(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=ORCL)))
   ```

---

### 📊 문제: 데이터베이스 성능 저하

#### 증상
- 쿼리 응답 시간 증가
- 연결 풀 고갈 경고
- 데드락 발생

#### 진단 방법
```bash
# Oracle DB 성능 통계
kubectl exec -it oracle-db-0 -- sqlplus gpu_admin/password@localhost:1521/ORCL <<EOF
SELECT name, value FROM v\$sysstat WHERE name IN ('execute count', 'parse count (total)', 'sorts (memory)', 'sorts (disk)');
SELECT sql_text, elapsed_time, executions FROM v\$sql WHERE elapsed_time > 1000000 ORDER BY elapsed_time DESC FETCH FIRST 10 ROWS ONLY;
EOF
```

#### 해결 방법
1. **인덱스 최적화**
   ```sql
   -- 메트릭 테이블 인덱스
   CREATE INDEX idx_gpu_metrics_device_time 
   ON gpu_usage_metrics(device_id, timestamp) PARALLEL 4;
   
   CREATE INDEX idx_gpu_metrics_timestamp 
   ON gpu_usage_metrics(timestamp) PARALLEL 4;
   
   -- 할당 테이블 인덱스
   CREATE INDEX idx_allocations_status_time 
   ON gpu_allocations(status, allocation_time) PARALLEL 4;
   
   -- 통계 정보 갱신
   EXEC DBMS_STATS.GATHER_TABLE_STATS('GPU_ADMIN', 'GPU_USAGE_METRICS');
   ```

2. **연결 풀 튜닝**
   ```yaml
   spring:
     datasource:
       hikari:
         maximum-pool-size: 30
         minimum-idle: 15
         connection-timeout: 30000
         idle-timeout: 600000
         max-lifetime: 1800000
         leak-detection-threshold: 60000
   ```

---

## 보안 및 권한 문제

### 🔐 문제: RBAC 권한 부족

#### 증상
- "Forbidden: cannot get resource" 오류
- Kubernetes API 접근 거부
- 401/403 HTTP 상태 코드

#### 진단 방법
```bash
# 현재 권한 확인
kubectl auth can-i get nodes --as=system:serviceaccount:gpu-management:gpu-management
kubectl auth can-i list pods --as=system:serviceaccount:gpu-management:gpu-management

# ServiceAccount 확인
kubectl get serviceaccount gpu-management -n gpu-management
kubectl get clusterrolebinding | grep gpu-management
```

#### 해결 방법
1. **필요한 권한 추가**
   ```yaml
   apiVersion: rbac.authorization.k8s.io/v1
   kind: ClusterRole
   metadata:
     name: gpu-management-extended
   rules:
   - apiGroups: [""]
     resources: ["nodes", "pods", "services", "endpoints"]
     verbs: ["get", "list", "watch"]
   - apiGroups: ["apps"]
     resources: ["deployments", "daemonsets"]
     verbs: ["get", "list", "watch"]
   - apiGroups: ["metrics.k8s.io"]
     resources: ["nodes", "pods"]
     verbs: ["get", "list"]
   ---
   apiVersion: rbac.authorization.k8s.io/v1
   kind: ClusterRoleBinding
   metadata:
     name: gpu-management-extended
   roleRef:
     apiGroup: rbac.authorization.k8s.io
     kind: ClusterRole
     name: gpu-management-extended
   subjects:
   - kind: ServiceAccount
     name: gpu-management
     namespace: gpu-management
   ```

---

### 🔑 문제: Secret 및 ConfigMap 접근 오류

#### 증상
- 환경 변수 값 누락
- "Could not resolve placeholder" 오류
- 설정 로드 실패

#### 진단 방법
```bash
# Secret 존재 확인
kubectl get secret gpu-management-secrets -n gpu-management

# ConfigMap 확인
kubectl get configmap gpu-management-config -n gpu-management

# Pod에서 마운트된 Secret 확인
kubectl exec -it deployment/k8s-gpu-management -n gpu-management -- \
  env | grep DB_PASSWORD
```

#### 해결 방법
1. **Secret 생성 및 마운트**
   ```bash
   # Secret 생성
   kubectl create secret generic gpu-management-secrets \
     --from-literal=db-password=your-password \
     --from-literal=slack-webhook-url=your-webhook-url \
     -n gpu-management
   ```

   ```yaml
   # Deployment에서 Secret 사용
   env:
   - name: DB_PASSWORD
     valueFrom:
       secretKeyRef:
         name: gpu-management-secrets
         key: db-password
   ```

---

## 일반적인 진단 및 복구 명령어

### 🔍 시스템 전체 상태 확인

```bash
#!/bin/bash
# 전체 시스템 상태 확인 스크립트

echo "=== Pod 상태 ==="
kubectl get pods -n gpu-management -o wide

echo "=== 서비스 상태 ==="
kubectl get svc,ep -n gpu-management

echo "=== 최근 이벤트 ==="
kubectl get events -n gpu-management --sort-by='.lastTimestamp' | tail -10

echo "=== 리소스 사용량 ==="
kubectl top pods -n gpu-management
kubectl top nodes

echo "=== GPU 장비 상태 ==="
curl -s http://localhost:8080/k8s-monitor/api/v1/gpu/devices | \
  jq '.data[] | {deviceId, deviceStatus, currentTempC, currentUtilization}'

echo "=== 활성 할당 ==="
curl -s http://localhost:8080/k8s-monitor/api/v1/gpu/allocations | \
  jq '.data[] | select(.status == "ALLOCATED") | {allocationId, podName, allocationTime}'

echo "=== 애플리케이션 헬스 ==="
curl -s http://localhost:8080/k8s-monitor/actuator/health | jq
```

### 🚨 긴급 복구 절차

```bash
#!/bin/bash
# 긴급 복구 스크립트

echo "1. 서비스 재시작..."
kubectl rollout restart deployment/k8s-gpu-management -n gpu-management

echo "2. 대기 중..."
kubectl rollout status deployment/k8s-gpu-management -n gpu-management --timeout=300s

echo "3. 만료된 할당 정리..."
curl -X POST http://localhost:8080/k8s-monitor/api/v1/gpu/allocations/cleanup

echo "4. 메트릭 수집 재시작..."
curl -X POST http://localhost:8080/k8s-monitor/api/v1/gpu/metrics/collection/restart

echo "5. 헬스 체크..."
curl -s http://localhost:8080/k8s-monitor/actuator/health | jq '.status'

echo "복구 완료!"
```

### 📋 지원 정보 수집

```bash
#!/bin/bash
# 지원 요청을 위한 정보 수집 스크립트

NAMESPACE="gpu-management"
OUTPUT_DIR="support-bundle-$(date +%Y%m%d_%H%M%S)"

mkdir -p "$OUTPUT_DIR"

echo "지원 정보 수집 중..."

# 기본 정보
kubectl get pods -n $NAMESPACE -o wide > "$OUTPUT_DIR/pods.txt"
kubectl get svc,ep -n $NAMESPACE > "$OUTPUT_DIR/services.txt"
kubectl get events -n $NAMESPACE --sort-by='.lastTimestamp' > "$OUTPUT_DIR/events.txt"

# 로그 수집
kubectl logs deployment/k8s-gpu-management -n $NAMESPACE --tail=2000 > "$OUTPUT_DIR/app-logs.txt"

# 설정 정보
kubectl get configmap -n $NAMESPACE -o yaml > "$OUTPUT_DIR/configmaps.yaml"
kubectl describe deployment k8s-gpu-management -n $NAMESPACE > "$OUTPUT_DIR/deployment-desc.txt"

# GPU 상태 정보
curl -s http://localhost:8080/k8s-monitor/api/v1/gpu/overview > "$OUTPUT_DIR/gpu-overview.json"
curl -s http://localhost:8080/k8s-monitor/api/v1/gpu/devices > "$OUTPUT_DIR/gpu-devices.json"
curl -s http://localhost:8080/k8s-monitor/api/v1/gpu/allocations > "$OUTPUT_DIR/gpu-allocations.json"

# 메트릭 정보
curl -s http://localhost:8080/k8s-monitor/actuator/prometheus > "$OUTPUT_DIR/prometheus-metrics.txt"
curl -s http://localhost:8080/k8s-monitor/actuator/health > "$OUTPUT_DIR/health.json"

# 시스템 정보
kubectl top pods -n $NAMESPACE > "$OUTPUT_DIR/resource-usage.txt"
kubectl top nodes > "$OUTPUT_DIR/node-usage.txt"

# 압축
tar -czf "$OUTPUT_DIR.tar.gz" "$OUTPUT_DIR"
echo "지원 정보가 $OUTPUT_DIR.tar.gz 파일에 저장되었습니다."
```

---

## 🆘 지원 및 에스컬레이션

### 연락처 정보
- **기술 지원**: gpu-support@company.com
- **긴급 연락처**: +82-10-xxxx-xxxx  
- **Slack 채널**: #gpu-management-support
- **GitHub Issues**: https://github.com/company/k8s-gpu-management/issues

### 지원 요청 시 포함할 정보
1. 발생 시간 및 기간
2. 오류 메시지 전문
3. 재현 단계
4. 시스템 환경 정보
5. 지원 번들 파일 (`support-bundle.tar.gz`)

이 문제 해결 가이드를 통해 대부분의 일반적인 문제들을 해결할 수 있습니다. 추가적인 도움이 필요한 경우 언제든지 지원팀에 연락하시기 바랍니다. 🚀