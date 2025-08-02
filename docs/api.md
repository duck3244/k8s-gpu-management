# K8s GPU Management API 문서

## 개요

이 문서는 Kubernetes GPU 관리 시스템의 REST API 사용법을 설명합니다.

## Base URL

```
http://your-server:8080/k8s-monitor/api/v1
```

## 인증

현재 버전은 기본 인증을 사용합니다. 향후 JWT 토큰 기반 인증이 추가될 예정입니다.

## 응답 형식

모든 API 응답은 JSON 형식입니다.

### 성공 응답
```json
{
  "status": "success",
  "data": { ... },
  "timestamp": "2025-08-03T12:00:00Z"
}
```

### 오류 응답
```json
{
  "status": "error",
  "error": {
    "code": "RESOURCE_NOT_FOUND",
    "message": "리소스를 찾을 수 없습니다",
    "details": "..."
  },
  "timestamp": "2025-08-03T12:00:00Z"
}
```

## 1. 기본 리소스 API

### 1.1 Pod 관리

#### 모델 서빙 Pod 목록 조회
```http
GET /resources/pods?namespace={namespace}
```

**파라미터:**
- `namespace` (선택): 특정 네임스페이스 필터

**응답 예시:**
```json
{
  "status": "success",
  "data": [
    {
      "name": "vllm-llama2-7b-chat-hf-6d8f9b8b8-abc12",
      "namespace": "model-serving",
      "nodeName": "gpu-worker-1",
      "phase": "Running",
      "modelType": "vllm",
      "modelName": "llama2-7b-chat-hf",
      "cpuUsagePercent": 45.2,
      "memoryUsagePercent": 67.8,
      "gpuUsagePercent": 89.1,
      "creationTime": "2025-08-03T10:30:00Z"
    }
  ]
}
```

#### 전체 Pod 목록 조회
```http
GET /resources/pods/all?namespace={namespace}
```

#### 특정 Pod 상세 정보
```http
GET /resources/pods/{namespace}/{podName}
```

### 1.2 Node 관리

#### 노드 리소스 정보 조회
```http
GET /resources/nodes
```

**응답 예시:**
```json
{
  "status": "success",
  "data": [
    {
      "name": "gpu-worker-1",
      "role": "worker",
      "status": "Ready",
      "cpuCapacity": "32",
      "memoryCapacity": "128Gi",
      "gpuCapacity": "8",
      "cpuUsagePercent": 45.2,
      "memoryUsagePercent": 67.8,
      "gpuUsagePercent": 75.0,
      "totalPodCount": 24,
      "runningPodCount": 22,
      "vllmPodCount": 8,
      "sglangPodCount": 4
    }
  ]
}
```

#### 특정 노드 상세 정보
```http
GET /resources/nodes/{nodeName}
```

### 1.3 통합 리소스 조회

#### 전체 리소스 사용량
```http
GET /resources/usage?namespace={namespace}
```

**응답 예시:**
```json
{
  "status": "success",
  "data": {
    "pods": [...],
    "nodes": [...],
    "clusterSummary": {
      "totalNodes": 4,
      "readyNodes": 4,
      "totalPods": 48,
      "runningPods": 45,
      "vllmPods": 18,
      "sglangPods": 12,
      "avgCpuUsage": 52.3,
      "avgMemoryUsage": 68.7,
      "avgGpuUsage": 73.1
    },
    "timestamp": "2025-08-03T12:00:00Z"
  }
}
```

## 2. GPU 관리 API

### 2.1 GPU 장비 관리

#### 전체 GPU 장비 조회
```http
GET /gpu/devices
```

**쿼리 파라미터:**
- `nodeName`: 특정 노드의 GPU만 조회
- `modelId`: 특정 모델의 GPU만 조회
- `status`: available, active, maintenance

**응답 예시:**
```json
{
  "status": "success",
  "data": [
    {
      "deviceId": "gpu-worker-1-GPU-00",
      "nodeName": "gpu-worker-1",
      "modelId": "A100_80GB",
      "modelName": "NVIDIA A100 80GB",
      "deviceIndex": 0,
      "deviceStatus": "ACTIVE",
      "currentTempC": 72.5,
      "currentPowerW": 280.3,
      "currentUtilization": 85.2,
      "memoryGb": 80,
      "architecture": "Ampere",
      "migSupport": true,
      "allocated": false
    }
  ]
}
```

#### 특정 GPU 장비 상세 정보
```http
GET /gpu/devices/{deviceId}
```

#### GPU 장비 등록
```http
POST /gpu/devices
```

**요청 본문:**
```json
{
  "nodeName": "gpu-worker-1",
  "modelId": "A100_80GB",
  "deviceIndex": 0,
  "serialNumber": "1234567890",
  "pciAddress": "0000:17:00.0",
  "gpuUuid": "GPU-12345678-1234-1234-1234-123456789012",
  "driverVersion": "470.129.06",
  "firmwareVersion": "92.00.36.00.01",
  "purchaseCost": 15000.0,
  "warrantyExpiryDate": "2026-08-03T00:00:00Z"
}
```

#### GPU 장비 상태 업데이트
```http
PUT /gpu/devices/{deviceId}/status?status={status}
```

**상태 값:**
- `ACTIVE`: 활성
- `INACTIVE`: 비활성
- `MAINTENANCE`: 점검 중
- `FAILED`: 장애
- `MIG_ENABLED`: MIG 활성화

#### GPU 장비 통계
```http
GET /gpu/devices/statistics
```

#### 과열 상태 GPU 조회
```http
GET /gpu/devices/overheating
```

#### GPU 장비 헬스 체크
```http
GET /gpu/devices/{deviceId}/health
```

### 2.2 MIG 관리

#### MIG 인스턴스 생성
```http
POST /gpu/mig/devices/{deviceId}
```

**요청 본문:**
```json
{
  "profileIds": ["1g.10gb", "2g.20gb", "1g.10gb"]
}
```

#### MIG 인스턴스 삭제
```http
DELETE /gpu/mig/devices/{deviceId}
```

#### 사용 가능한 MIG 인스턴스 조회
```http
GET /gpu/mig/available
```

#### 특정 장비의 MIG 인스턴스 조회
```http
GET /gpu/mig/devices/{deviceId}
```

#### MIG 사용률 통계
```http
GET /gpu/mig/statistics
```

### 2.3 GPU 할당 관리

#### GPU 리소스 할당
```http
POST /gpu/allocations
```

**요청 본문:**
```json
{
  "namespace": "model-serving",
  "podName": "vllm-llama2-7b",
  "containerName": "vllm-container",
  "workloadType": "Inference",
  "useMig": true,
  "requiredMemoryGb": 20,
  "preferredModelId": "A100_80GB",
  "plannedReleaseTime": "2025-08-04T12:00:00Z",
  "userId": "user123",
  "teamId": "ml-team",
  "projectId": "llm-project",
  "maxCostPerHour": 6.0
}
```

**응답 예시:**
```json
{
  "status": "success",
  "data": {
    "allocationId": "ALLOC-ABC12345",
    "namespace": "model-serving",
    "podName": "vllm-llama2-7b",
    "resourceType": "MIG_INSTANCE",
    "allocatedResource": "gpu-worker-1-GPU-00-MIG-01",
    "allocatedMemoryGb": 20,
    "allocationTime": "2025-08-03T12:00:00Z",
    "plannedReleaseTime": "2025-08-04T12:00:00Z",
    "status": "ALLOCATED",
    "costPerHour": 4.2
  }
}
```

#### GPU 리소스 해제
```http
DELETE /gpu/allocations/{allocationId}
```

#### 활성 할당 조회
```http
GET /gpu/allocations
```

**쿼리 파라미터:**
- `namespace`: 네임스페이스별 필터
- `userId`: 사용자별 필터
- `teamId`: 팀별 필터

#### 할당 비용 통계
```http
GET /gpu/allocations/cost-statistics
```

#### 만료 예정 할당 조회
```http
GET /gpu/allocations/expiring?hours={hours}
```

### 2.4 GPU 메트릭

#### GPU 사용량 통계
```http
GET /gpu/metrics/usage-statistics?hours={hours}
```

**응답 예시:**
```json
{
  "status": "success",
  "data": {
    "timeRange": "24 hours",
    "deviceStatistics": {
      "gpu-worker-1-GPU-00": {
        "modelName": "NVIDIA A100 80GB",
        "avgGpuUtilization": 78.5,
        "avgMemoryUtilization": 65.2,
        "avgTemperature": 74.3
      }
    },
    "modelStatistics": {
      "A100_80GB": {
        "deviceCount": 4,
        "avgGpuUtilization": 75.8,
        "avgMemoryUtilization": 68.1,
        "avgTemperature": 73.6
      }
    }
  }
}
```

#### 과열 알람 조회
```http
GET /gpu/metrics/overheating-alerts
```

#### 메트릭 수집 트리거
```http
POST /gpu/metrics/collect
```

#### 메트릭 수집 상태
```http
GET /gpu/metrics/collection-status
```

### 2.5 GPU 비용 분석

#### GPU 비용 분석
```http
GET /gpu/cost/analysis?days={days}
```

**응답 예시:**
```json
{
  "status": "success",
  "data": {
    "dailyCost": 456.78,
    "weeklyCost": 3197.46,
    "monthlyCost": 13703.40,
    "costByNamespace": {
      "model-serving": 8234.56,
      "research": 3456.78,
      "development": 2012.06
    },
    "costByTeam": {
      "ml-team": 6789.12,
      "research-team": 4567.89,
      "dev-team": 2346.39
    },
    "potentialMonthlySavings": 2456.78,
    "analysisDate": "2025-08-03T12:00:00Z"
  }
}
```

#### 비용 최적화 제안
```http
GET /gpu/cost/optimization
```

### 2.6 GPU 클러스터 관리

#### 클러스터 개요
```http
GET /gpu/overview
```

**응답 예시:**
```json
{
  "status": "success",
  "data": {
    "totalNodes": 4,
    "totalGpuDevices": 32,
    "totalMigInstances": 64,
    "activeAllocations": 28,
    "devicesByModel": {
      "A100_80GB": 16,
      "H100_80GB": 8,
      "RTX4090": 8
    },
    "overallGpuUtilization": 75.3,
    "overallMemoryUtilization": 68.7,
    "overallTemperature": 73.2,
    "totalHourlyCost": 192.45,
    "totalMonthlyCost": 13856.40,
    "totalAlerts": 3,
    "criticalAlerts": 0,
    "warningAlerts": 3
  }
}
```

#### GPU 사용량 예측
```http
GET /gpu/forecast?hours={hours}
```

#### GPU 클러스터 헬스 상태
```http
GET /gpu/health
```

## 3. 분석 및 통계 API

### 3.1 리소스 분석

#### 상위 리소스 사용 Pod
```http
GET /resources/pods/top?resourceType={type}&limit={limit}
```

**파라미터:**
- `resourceType`: cpu, memory, gpu
- `limit`: 결과 개수 (기본값: 10)

#### 클러스터 헬스 상태
```http
GET /resources/health
```

#### 리소스 사용량 통계
```http
GET /resources/statistics?hours={hours}
```

#### 네임스페이스별 리소스 사용량
```http
GET /resources/namespaces
```

#### 리소스 알람
```http
GET /resources/alerts
```

#### 리소스 사용량 예측
```http
GET /resources/forecast?hours={hours}
```

#### 메트릭 서버 상태
```http
GET /resources/metrics/status
```

## 4. 에러 코드

| 코드 | 설명 |
|------|------|
| `RESOURCE_NOT_FOUND` | 리소스를 찾을 수 없음 |
| `GPU_NOT_AVAILABLE` | GPU 사용 불가 |
| `MIG_NOT_SUPPORTED` | MIG 미지원 |
| `ALLOCATION_FAILED` | 할당 실패 |
| `INVALID_PARAMETER` | 잘못된 파라미터 |
| `INSUFFICIENT_RESOURCES` | 리소스 부족 |
| `COST_LIMIT_EXCEEDED` | 비용 한도 초과 |
| `VALIDATION_ERROR` | 유효성 검증 실패 |
| `UNAUTHORIZED` | 인증 실패 |
| `FORBIDDEN` | 권한 부족 |
| `INTERNAL_ERROR` | 내부 서버 오류 |

## 5. 웹훅 API

### 5.1 알람 웹훅

GPU 관리 시스템은 중요한 이벤트 발생 시 웹훅을 통해 알림을 전송할 수 있습니다.

#### 웹훅 등록
```http
POST /webhooks
```

**요청 본문:**
```json
{
  "name": "slack-notifications",
  "url": "https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX",
  "events": ["GPU_OVERHEATING", "ALLOCATION_FAILED", "COST_THRESHOLD_EXCEEDED"],
  "secret": "webhook_secret_key",
  "active": true
}
```

#### 웹훅 페이로드 예시
```json
{
  "event": "GPU_OVERHEATING",
  "timestamp": "2025-08-03T12:00:00Z",
  "data": {
    "deviceId": "gpu-worker-1-GPU-00",
    "temperature": 95.2,
    "threshold": 85.0,
    "severity": "CRITICAL"
  },
  "signature": "sha256=..."
}
```

## 6. 배치 API

### 6.1 배치 작업

#### 배치 메트릭 수집
```http
POST /batch/metrics/collect
```

**요청 본문:**
```json
{
  "nodes": ["gpu-worker-1", "gpu-worker-2"],
  "collectGpuMetrics": true,
  "collectNodeMetrics": true,
  "timeRange": "1h"
}
```

#### 배치 할당 생성
```http
POST /batch/allocations
```

**요청 본문:**
```json
{
  "allocations": [
    {
      "namespace": "training",
      "podName": "pytorch-job-1",
      "workloadType": "Training",
      "requiredMemoryGb": 40,
      "useMig": false
    },
    {
      "namespace": "training", 
      "podName": "pytorch-job-2",
      "workloadType": "Training",
      "requiredMemoryGb": 20,
      "useMig": true
    }
  ]
}
```

## 7. SDK 및 클라이언트 라이브러리

### 7.1 Python SDK

```python
from k8s_gpu_client import GpuManagementClient

# 클라이언트 초기화
client = GpuManagementClient(
    base_url="http://your-server:8080/k8s-monitor/api/v1",
    api_key="your-api-key"
)

# GPU 장비 조회
devices = client.devices.list()

# GPU 할당
allocation = client.allocations.create(
    namespace="model-serving",
    pod_name="vllm-pod",
    workload_type="Inference",
    required_memory_gb=20,
    use_mig=True
)

# 비용 분석
cost_analysis = client.cost.analyze(days=30)
```

### 7.2 Java SDK

```java
import com.k8s.monitor.client.GpuManagementClient;
import com.k8s.monitor.client.model.*;

// 클라이언트 초기화
GpuManagementClient client = GpuManagementClient.builder()
    .baseUrl("http://your-server:8080/k8s-monitor/api/v1")
    .apiKey("your-api-key")
    .build();

// GPU 장비 조회
List<GpuDeviceInfo> devices = client.devices().list();

// GPU 할당
GpuAllocationRequest request = GpuAllocationRequest.builder()
    .namespace("model-serving")
    .podName("vllm-pod")
    .workloadType("Inference")
    .requiredMemoryGb(20)
    .useMig(true)
    .build();
    
GpuAllocationInfo allocation = client.allocations().create(request);
```

### 7.3 Go SDK

```go
package main

import (
    "github.com/your-org/k8s-gpu-client-go"
)

func main() {
    // 클라이언트 초기화
    client := gpuclient.NewClient(&gpuclient.Config{
        BaseURL: "http://your-server:8080/k8s-monitor/api/v1",
        APIKey:  "your-api-key",
    })

    // GPU 장비 조회
    devices, err := client.Devices.List(context.Background(), nil)
    if err != nil {
        log.Fatal(err)
    }

    // GPU 할당
    allocation, err := client.Allocations.Create(context.Background(), &gpuclient.AllocationRequest{
        Namespace:        "model-serving",
        PodName:         "vllm-pod",
        WorkloadType:    "Inference",
        RequiredMemoryGB: 20,
        UseMIG:          true,
    })
}
```

## 8. API 제한 및 쿼터

### 8.1 Rate Limiting

- **기본 제한**: 분당 1000 요청
- **인증된 사용자**: 분당 5000 요청
- **배치 API**: 분당 100 요청

### 8.2 응답 헤더

```http
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 999
X-RateLimit-Reset: 1625097600
```

### 8.3 쿼터 초과 시 응답

```json
{
  "status": "error",
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "요청 한도를 초과했습니다",
    "retryAfter": 60
  }
}
```

## 9. API 버전 관리

### 9.1 버전 정책

- **현재 버전**: v1
- **지원 버전**: v1
- **향후 버전**: v2 (계획 중)

### 9.2 호환성

- v1: 안정적, 장기 지원
- 하위 호환성 보장
- Deprecation 정책: 6개월 사전 공지

## 10. 예제 시나리오

### 10.1 vLLM 모델 배포 시나리오

```bash
# 1. 사용 가능한 GPU 확인
curl -X GET "${API_BASE}/gpu/devices?status=available"

# 2. GPU 할당
curl -X POST "${API_BASE}/gpu/allocations" \
  -H "Content-Type: application/json" \
  -d '{
    "namespace": "model-serving",
    "podName": "vllm-llama2-7b",
    "workloadType": "Inference",
    "requiredMemoryGb": 30,
    "useMig": false,
    "userId": "ml-engineer-1",
    "teamId": "ml-team"
  }'

# 3. 할당 상태 확인
curl -X GET "${API_BASE}/gpu/allocations?namespace=model-serving"

# 4. GPU 사용률 모니터링
curl -X GET "${API_BASE}/gpu/metrics/usage-statistics?hours=1"

# 5. 비용 추적
curl -X GET "${API_BASE}/gpu/cost/analysis?days=1"
```

### 10.2 MIG 인스턴스 관리 시나리오

```bash
# 1. MIG 지원 GPU 확인
curl -X GET "${API_BASE}/gpu/devices?modelId=A100_80GB"

# 2. MIG 인스턴스 생성
curl -X POST "${API_BASE}/gpu/mig/devices/gpu-worker-1-GPU-00" \
  -H "Content-Type: application/json" \
  -d '{
    "profileIds": ["1g.10gb", "2g.20gb", "4g.40gb"]
  }'

# 3. 사용 가능한 MIG 인스턴스 조회
curl -X GET "${API_BASE}/gpu/mig/available"

# 4. MIG 인스턴스에 할당
curl -X POST "${API_BASE}/gpu/allocations" \
  -H "Content-Type: application/json" \
  -d '{
    "namespace": "development",
    "podName": "jupyter-notebook",
    "workloadType": "Development",
    "useMig": true,
    "requiredMemoryGb": 10
  }'
```

### 10.3 비용 최적화 시나리오

```bash
# 1. 현재 비용 분석
curl -X GET "${API_BASE}/gpu/cost/analysis?days=30"

# 2. 최적화 제안 조회
curl -X GET "${API_BASE}/gpu/cost/optimization"

# 3. 유휴 리소스 확인
curl -X GET "${API_BASE}/gpu/devices?status=available"

# 4. 장기 실행 할당 조회
curl -X GET "${API_BASE}/gpu/allocations/expiring?hours=168"

# 5. 비용 통계 확인
curl -X GET "${API_BASE}/gpu/allocations/cost-statistics"
```

## 11. 문제 해결

### 11.1 일반적인 API 오류

#### 401 Unauthorized
```bash
# 인증 정보 확인
curl -X GET "${API_BASE}/gpu/devices" \
  -H "Authorization: Bearer your-token"
```

#### 404 Not Found
```bash
# 리소스 존재 여부 확인
curl -X GET "${API_BASE}/gpu/devices"
curl -X GET "${API_BASE}/gpu/devices/invalid-device-id"
```

#### 500 Internal Server Error
```bash
# 서버 상태 확인
curl -X GET "${API_BASE}/../actuator/health"

# 로그 확인
kubectl logs deployment/k8s-gpu-management -n gpu-management
```

### 11.2 디버깅 팁

#### API 응답 시간 측정
```bash
curl -w "@curl-format.txt" -o /dev/null -s "${API_BASE}/gpu/overview"
```

**curl-format.txt:**
```
     time_namelookup:  %{time_namelookup}\n
        time_connect:  %{time_connect}\n
     time_appconnect:  %{time_appconnect}\n
    time_pretransfer:  %{time_pretransfer}\n
       time_redirect:  %{time_redirect}\n
  time_starttransfer:  %{time_starttransfer}\n
                     ----------\n
          time_total:  %{time_total}\n
```

#### API 상태 모니터링
```bash
# 헬스 체크
watch -n 5 "curl -s ${API_BASE}/../actuator/health | jq '.status'"

# 메트릭 확인
curl -s "${API_BASE}/../actuator/metrics" | jq '.names[]' | grep gpu
```

## 12. 추가 리소스

- **OpenAPI 스펙**: `/api-docs`
- **Swagger UI**: `/swagger-ui.html`
- **API 테스트 컬렉션**: [Postman Collection](./postman-collection.json)
- **예제 스크립트**: [GitHub Examples](https://github.com/your-org/k8s-gpu-management/tree/main/examples)

## 13. 지원 및 피드백

- **API 문의**: api-support@your-org.com
- **버그 리포트**: [GitHub Issues](https://github.com/your-org/k8s-gpu-management/issues)
- **기능 요청**: [Feature Requests](https://github.com/your-org/k8s-gpu-management/discussions)
- **커뮤니티**: [Slack #gpu-management](https://your-org.slack.com/channels/gpu-management)