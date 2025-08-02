# K8s GPU Management 통합 가이드

## 개요

이 문서는 Kubernetes GPU 관리 시스템을 다른 시스템 및 도구와 통합하는 방법을 설명합니다.

## 1. CI/CD 파이프라인 통합

### 1.1 GitLab CI/CD 통합

#### .gitlab-ci.yml
```yaml
stages:
  - build
  - test
  - deploy
  - gpu-validation

variables:
  DOCKER_REGISTRY: "registry.company.com"
  APP_NAME: "k8s-gpu-management"
  NAMESPACE: "gpu-management"

# 빌드 단계
build:
  stage: build
  image: docker:20.10.16
  services:
    - docker:20.10.16-dind
  script:
    - docker build -t $DOCKER_REGISTRY/$APP_NAME:$CI_COMMIT_SHA .
    - docker push $DOCKER_REGISTRY/$APP_NAME:$CI_COMMIT_SHA
  only:
    - main
    - develop

# 테스트 단계
test:
  stage: test
  image: openjdk:17-jdk
  script:
    - ./mvnw clean test
    - ./mvnw jacoco:report
  artifacts:
    reports:
      junit: target/surefire-reports/TEST-*.xml
      coverage: target/site/jacoco/jacoco.xml
  coverage: '/Total.*?([0-9]{1,3})%/'

# GPU 클러스터 배포
deploy-gpu:
  stage: deploy
  image: bitnami/kubectl:latest
  environment:
    name: gpu-cluster
    url: https://gpu-management.company.com
  script:
    - kubectl config use-context gpu-cluster
    - envsubst < k8s/deployment.yaml | kubectl apply -f -
    - kubectl rollout status deployment/$APP_NAME -n $NAMESPACE
  only:
    - main

# GPU 시스템 검증
gpu-validation:
  stage: gpu-validation
  image: curlimages/curl:latest
  script:
    - sleep 60  # 배포 대기
    - |
      # GPU 장비 검증
      response=$(curl -s "https://gpu-management.company.com/api/v1/gpu/devices")
      device_count=$(echo $response | jq '.data | length')
      if [ "$device_count" -lt 1 ]; then
        echo "ERROR: No GPU devices detected"
        exit 1
      fi
      echo "SUCCESS: $device_count GPU devices detected"
      
      # 헬스 체크
      health=$(curl -s "https://gpu-management.company.com/actuator/health")
      status=$(echo $health | jq -r '.status')
      if [ "$status" != "UP" ]; then
        echo "ERROR: Health check failed"
        exit 1
      fi
      echo "SUCCESS: Application health check passed"
  only:
    - main
```

### 1.2 GitHub Actions 통합

#### .github/workflows/gpu-management.yml
```yaml
name: GPU Management CI/CD

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}

jobs:
  build:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write

    steps:
    - name: Checkout repository
      uses: actions/checkout@v3

    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'

    - name: Cache Maven packages
      uses: actions/cache@v3
      with:
        path: ~/.m2
        key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}

    - name: Run tests
      run: ./mvnw clean test

    - name: Log in to Container Registry
      uses: docker/login-action@v2
      with:
        registry: ${{ env.REGISTRY }}
        username: ${{ github.actor }}
        password: ${{ secrets.GITHUB_TOKEN }}

    - name: Extract metadata
      id: meta
      uses: docker/metadata-action@v4
      with:
        images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}
        tags: |
          type=ref,event=branch
          type=ref,event=pr
          type=sha

    - name: Build and push Docker image
      uses: docker/build-push-action@v4
      with:
        context: .
        push: true
        tags: ${{ steps.meta.outputs.tags }}
        labels: ${{ steps.meta.outputs.labels }}

  deploy:
    needs: build
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    
    steps:
    - name: Checkout repository
      uses: actions/checkout@v3

    - name: Configure kubectl
      uses: azure/k8s-set-context@v1
      with:
        method: kubeconfig
        kubeconfig: ${{ secrets.KUBE_CONFIG }}

    - name: Deploy to Kubernetes
      run: |
        envsubst < k8s/deployment.yaml | kubectl apply -f -
        kubectl rollout status deployment/k8s-gpu-management -n gpu-management

    - name: Run GPU validation tests
      run: |
        kubectl wait --for=condition=ready pod -l app=k8s-gpu-management -n gpu-management --timeout=300s
        
        # Port forward for testing
        kubectl port-forward svc/k8s-gpu-management 8080:8080 -n gpu-management &
        sleep 10
        
        # Run validation tests
        python scripts/validate-gpu-system.py
```

### 1.3 Jenkins Pipeline 통합

#### Jenkinsfile
```groovy
pipeline {
    agent any
    
    environment {
        DOCKER_REGISTRY = 'registry.company.com'
        APP_NAME = 'k8s-gpu-management'
        NAMESPACE = 'gpu-management'
        KUBECONFIG = credentials('gpu-cluster-kubeconfig')
    }
    
    stages {
        stage('Checkout') {
            steps {
                git branch: 'main', url: 'https://github.com/company/k8s-gpu-management.git'
            }
        }
        
        stage('Build & Test') {
            parallel {
                stage('Maven Build') {
                    steps {
                        sh './mvnw clean package -DskipTests'
                    }
                }
                stage('Unit Tests') {
                    steps {
                        sh './mvnw test'
                    }
                    post {
                        always {
                            junit 'target/surefire-reports/*.xml'
                            publishCoverage adapters: [jacocoAdapter('target/site/jacoco/jacoco.xml')]
                        }
                    }
                }
            }
        }
        
        stage('Docker Build') {
            steps {
                script {
                    def image = docker.build("${DOCKER_REGISTRY}/${APP_NAME}:${BUILD_NUMBER}")
                    docker.withRegistry("https://${DOCKER_REGISTRY}", 'docker-registry-credentials') {
                        image.push()
                        image.push('latest')
                    }
                }
            }
        }
        
        stage('Deploy to GPU Cluster') {
            when {
                branch 'main'
            }
            steps {
                script {
                    sh """
                        sed 's|IMAGE_TAG|${BUILD_NUMBER}|g' k8s/deployment.yaml | kubectl apply -f -
                        kubectl rollout status deployment/${APP_NAME} -n ${NAMESPACE}
                    """
                }
            }
        }
        
        stage('GPU System Validation') {
            when {
                branch 'main'
            }
            steps {
                script {
                    sh """
                        # Wait for deployment
                        sleep 60
                        
                        # Run GPU validation script
                        python3 scripts/gpu-validation.py --cluster gpu-cluster
                        
                        # Check GPU metrics collection
                        kubectl exec deployment/${APP_NAME} -n ${NAMESPACE} -- \\
                            curl -f http://localhost:8080/k8s-monitor/api/v1/gpu/devices
                    """
                }
            }
        }
    }
    
    post {
        success {
            slackSend channel: '#gpu-management', 
                      color: 'good',
                      message: "✅ GPU Management deployment successful - Build #${BUILD_NUMBER}"
        }
        failure {
            slackSend channel: '#gpu-management', 
                      color: 'danger',
                      message: "❌ GPU Management deployment failed - Build #${BUILD_NUMBER}"
        }
    }
}
```

## 2. 모니터링 시스템 통합

### 2.1 Prometheus 통합

#### prometheus.yml
```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  - "gpu-management-rules.yml"

scrape_configs:
  # GPU Management Application
  - job_name: 'gpu-management'
    kubernetes_sd_configs:
    - role: pod
      namespaces:
        names:
        - gpu-management
    relabel_configs:
    - source_labels: [__meta_kubernetes_pod_label_app]
      action: keep
      regex: k8s-gpu-management
    - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
      action: keep
      regex: true
    - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_path]
      action: replace
      target_label: __metrics_path__
      regex: (.+)

  # NVIDIA DCGM Exporter (GPU 하드웨어 메트릭)
  - job_name: 'dcgm-exporter'
    kubernetes_sd_configs:
    - role: pod
      namespaces:
        names:
        - monitoring
    relabel_configs:
    - source_labels: [__meta_kubernetes_pod_label_app]
      action: keep
      regex: dcgm-exporter

alerting:
  alertmanagers:
  - static_configs:
    - targets:
      - alertmanager:9093
```

#### GPU 관련 알람 규칙 (gpu-management-rules.yml)
```yaml
groups:
- name: gpu-management.rules
  rules:
  # GPU 온도 알람
  - alert: GPUHighTemperature
    expr: DCGM_FI_DEV_GPU_TEMP > 85
    for: 5m
    labels:
      severity: warning
      component: gpu-hardware
    annotations:
      summary: "GPU {{ $labels.gpu }} temperature is high"
      description: "GPU {{ $labels.gpu }} on {{ $labels.instance }} has temperature {{ $value }}°C"

  # GPU 사용률 알람
  - alert: GPUHighUtilization
    expr: DCGM_FI_DEV_GPU_UTIL > 95
    for: 10m
    labels:
      severity: warning
      component: gpu-hardware
    annotations:
      summary: "GPU {{ $labels.gpu }} utilization is high"
      description: "GPU {{ $labels.gpu }} utilization is {{ $value }}%"

  # GPU 할당 실패 알람
  - alert: GPUAllocationFailure
    expr: increase(gpu_allocation_failures_total[5m]) > 0
    labels:
      severity: critical
      component: gpu-management
    annotations:
      summary: "GPU allocation failures detected"
      description: "{{ $value }} GPU allocation failures in the last 5 minutes"

  # GPU 관리 애플리케이션 다운
  - alert: GPUManagementDown
    expr: up{job="gpu-management"} == 0
    for: 2m
    labels:
      severity: critical
      component: gpu-management
    annotations:
      summary: "GPU Management application is down"
      description: "GPU Management application has been down for more than 2 minutes"

  # MIG 인스턴스 부족
  - alert: MIGInstancesLow
    expr: (gpu_mig_instances_available / gpu_mig_instances_total) * 100 < 10
    for: 5m
    labels:
      severity: warning
      component: mig-management
    annotations:
      summary: "Available MIG instances are running low"
      description: "Only {{ $value }}% of MIG instances are available"

  # 비용 임계값 초과
  - alert: GPUCostThresholdExceeded
    expr: gpu_daily_cost > 1000
    labels:
      severity: warning
      component: cost-management
    annotations:
      summary: "Daily GPU cost threshold exceeded"
      description: "Daily GPU cost is ${{ $value }}, exceeding threshold"
```

### 2.2 Grafana 대시보드 통합

#### GPU 관리 대시보드 JSON
```json
{
  "dashboard": {
    "id": null,
    "title": "GPU Management Dashboard",
    "tags": ["gpu", "kubernetes", "management"],
    "timezone": "browser",
    "panels": [
      {
        "id": 1,
        "title": "GPU Cluster Overview",
        "type": "stat",
        "targets": [
          {
            "expr": "count(up{job=\"dcgm-exporter\"})",
            "legendFormat": "Total GPUs"
          },
          {
            "expr": "count(up{job=\"dcgm-exporter\"} == 1)",
            "legendFormat": "Active GPUs"
          },
          {
            "expr": "gpu_allocations_active",
            "legendFormat": "Active Allocations"
          }
        ],
        "gridPos": {"h": 4, "w": 24, "x": 0, "y": 0}
      },
      {
        "id": 2,
        "title": "GPU Utilization by Node",
        "type": "heatmap",
        "targets": [
          {
            "expr": "avg by (instance) (DCGM_FI_DEV_GPU_UTIL)",
            "legendFormat": "{{ instance }}"
          }
        ],
        "gridPos": {"h": 8, "w": 12, "x": 0, "y": 4}
      },
      {
        "id": 3,
        "title": "GPU Temperature",
        "type": "graph",
        "targets": [
          {
            "expr": "DCGM_FI_DEV_GPU_TEMP",
            "legendFormat": "GPU {{ gpu }} - {{ instance }}"
          }
        ],
        "yAxes": [
          {
            "label": "Temperature (°C)",
            "max": 100,
            "min": 0
          }
        ],
        "gridPos": {"h": 8, "w": 12, "x": 12, "y": 4}
      },
      {
        "id": 4,
        "title": "MIG Instance Allocation",
        "type": "piechart",
        "targets": [
          {
            "expr": "gpu_mig_instances_allocated",
            "legendFormat": "Allocated"
          },
          {
            "expr": "gpu_mig_instances_available",
            "legendFormat": "Available"
          }
        ],
        "gridPos": {"h": 6, "w": 8, "x": 0, "y": 12}
      },
      {
        "id": 5,
        "title": "GPU Cost Trends",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(gpu_cost_total[1h]) * 3600",
            "legendFormat": "Hourly Cost"
          }
        ],
        "yAxes": [
          {
            "label": "Cost ($)",
            "min": 0
          }
        ],
        "gridPos": {"h": 6, "w": 8, "x": 8, "y": 12}
      }
    ],
    "time": {
      "from": "now-1h",
      "to": "now"
    },
    "refresh": "30s"
  }
}
```

## 3. 로그 관리 시스템 통합

### 3.1 ELK Stack 통합

#### Filebeat 설정
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: filebeat-config
  namespace: gpu-management
data:
  filebeat.yml: |
    filebeat.inputs:
    - type: container
      paths:
        - /var/log/containers/*gpu-management*.log
      processors:
      - add_kubernetes_metadata:
          host: ${NODE_NAME}
          matchers:
          - logs_path:
              logs_path: "/var/log/containers/"

    output.elasticsearch:
      hosts: ["elasticsearch.logging.svc.cluster.local:9200"]
      index: "gpu-management-%{+yyyy.MM.dd}"

    setup.template.name: "gpu-management"
    setup.template.pattern: "gpu-management-*"
    setup.template.settings:
      index.number_of_shards: 1
      index.number_of_replicas: 0

    logging.level: info
    logging.to_files: true
    logging.files:
      path: /var/log/filebeat
      name: filebeat
      keepfiles: 7
      permissions: 0644
```

#### Logstash 파이프라인
```ruby
# logstash-gpu-management.conf
input {
  beats {
    port => 5044
  }
}

filter {
  if [kubernetes][container][name] == "k8s-gpu-management" {
    # GPU 관련 로그 파싱
    if [message] =~ /GPU/ {
      grok {
        match => { 
          "message" => "%{TIMESTAMP_ISO8601:timestamp} %{LOGLEVEL:level} %{JAVACLASS:class} - %{GREEDYDATA:message_content}"
        }
      }
      
      # GPU 메트릭 추출
      if [message_content] =~ /device.*temperature.*(\d+\.\d+)/ {
        grok {
          match => {
            "message_content" => "device (?<device_id>\S+) temperature (?<temperature>\d+\.\d+)"
          }
        }
        mutate {
          convert => { "temperature" => "float" }
        }
      }
      
      # 할당 이벤트 추출
      if [message_content] =~ /allocation/ {
        grok {
          match => {
            "message_content" => "allocation (?<allocation_id>\S+) (?<allocation_action>\w+)"
          }
        }
      }
    }
    
    # 오류 로그 태깅
    if [level] == "ERROR" {
      mutate {
        add_tag => [ "error", "gpu-management" ]
      }
    }
  }
}

output {
  if [kubernetes][container][name] == "k8s-gpu-management" {
    elasticsearch {
      hosts => ["elasticsearch.logging.svc.cluster.local:9200"]
      index => "gpu-management-%{+YYYY.MM.dd}"
    }
  }
}
```

### 3.2 Fluentd 통합

#### fluentd-gpu-management.conf
```xml
<source>
  @type tail
  @id in_tail_gpu_management
  path /var/log/containers/*gpu-management*.log
  pos_file /var/log/fluentd-gpu-management.log.pos
  tag kubernetes.gpu-management
  read_from_head true
  <parse>
    @type multi_format
    <pattern>
      format json
      time_key time
      time_type string
      time_format %Y-%m-%dT%H:%M:%S.%NZ
    </pattern>
  </parse>
</source>

<filter kubernetes.gpu-management>
  @type kubernetes_metadata
  @id filter_kube_metadata_gpu
  kubernetes_url "#{ENV['KUBERNETES_SERVICE_HOST']}:#{ENV['KUBERNETES_SERVICE_PORT_HTTPS']}"
  verify_ssl "#{ENV['KUBERNETES_VERIFY_SSL'] || true}"
  ca_file "#{ENV['KUBERNETES_CA_FILE']}"
</filter>

<filter kubernetes.gpu-management>
  @type parser
  @id filter_gpu_logs
  key_name log
  reserve_data true
  remove_key_name_field true
  <parse>
    @type regexp
    expression /^(?<timestamp>\d{4}-\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}\.\d{3})\s+(?<level>\w+)\s+(?<class>[\w\.]+)\s+-\s+(?<message>.*)$/
  </parse>
</filter>

<filter kubernetes.gpu-management>
  @type record_transformer
  @id filter_gpu_enrichment
  <record>
    cluster_name "#{ENV['CLUSTER_NAME'] || 'gpu-cluster'}"
    environment "#{ENV['ENVIRONMENT'] || 'production'}"
  </record>
</filter>

<match kubernetes.gpu-management>
  @type elasticsearch
  @id out_es_gpu_management
  host elasticsearch.logging.svc.cluster.local
  port 9200
  logstash_format true
  logstash_prefix gpu-management
  type_name _doc
  include_tag_key true
  tag_key @log_name
  <buffer>
    @type file
    path /var/log/fluentd-buffers/gpu-management
    flush_mode interval
    retry_type exponential_backoff
    flush_thread_count 2
    flush_interval 5s
    retry_forever
    retry_max_interval 30
    chunk_limit_size 2M
    queue_limit_length 8
    overflow_action block
  </buffer>
</match>
```

## 4. 알림 시스템 통합

### 4.1 Slack 통합

#### Slack 앱 설정 및 WebHook
```bash
# Slack 앱 생성 및 권한 설정
# 1. https://api.slack.com/apps 에서 새 앱 생성
# 2. Incoming Webhooks 활성화
# 3. 채널 선택 및 WebHook URL 생성

# Kubernetes Secret 생성
kubectl create secret generic slack-webhook \
  --from-literal=url='https://hooks.slack.com/services/.../.../XXXXXXXXXX' \
  -n gpu-management
```

#### 커스텀 Slack 메시지 템플릿
```yaml
gpu:
  management:
    alerts:
      notification:
        slack:
          templates:
            gpu-overheating: |
              {
                "blocks": [
                  {
                    "type": "header",
                    "text": {
                      "type": "plain_text",
                      "text": "🌡️ GPU 과열 알림"
                    }
                  },
                  {
                    "type": "section",
                    "fields": [
                      {
                        "type": "mrkdwn",
                        "text": "*장비 ID:*\n{{deviceId}}"
                      },
                      {
                        "type": "mrkdwn",
                        "text": "*현재 온도:*\n{{temperature}}°C"
                      },
                      {
                        "type": "mrkdwn",
                        "text": "*임계값:*\n{{threshold}}°C"
                      },
                      {
                        "type": "mrkdwn",
                        "text": "*발생 시간:*\n{{timestamp}}"
                      }
                    ]
                  },
                  {
                    "type": "actions",
                    "elements": [
                      {
                        "type": "button",
                        "text": {
                          "type": "plain_text",
                          "text": "대시보드 보기"
                        },
                        "url": "https://gpu-management.company.com/dashboard"
                      }
                    ]
                  }
                ]
              }
            
            allocation-success: |
              {
                "text": "✅ GPU 할당 성공",
                "attachments": [
                  {
                    "color": "good",
                    "fields": [
                      {
                        "title": "할당 ID",
                        "value": "{{allocationId}}",
                        "short": true
                      },
                      {
                        "title": "Pod",
                        "value": "{{namespace}}/{{podName}}",
                        "short": true
                      },
                      {
                        "title": "GPU 리소스",
                        "value": "{{allocatedResource}}",
                        "short": true
                      },
                      {
                        "title": "비용/시간",
                        "value": "${{costPerHour}}",
                        "short": true
                      }
                    ]
                  }
                ]
              }
```

### 4.2 Microsoft Teams 통합

#### Teams WebHook 설정
```yaml
gpu:
  management:
    alerts:
      notification:
        teams:
          enabled: true
          webhook-url: "${TEAMS_WEBHOOK_URL}"
          templates:
            gpu-alert: |
              {
                "@type": "MessageCard",
                "@context": "https://schema.org/extensions",
                "themeColor": "{{#if critical}}FF0000{{else}}FFA500{{/if}}",
                "summary": "GPU 관리 알림",
                "sections": [
                  {
                    "activityTitle": "{{alertType}}",
                    "activitySubtitle": "GPU 클러스터에서 이벤트 발생",
                    "facts": [
                      {
                        "name": "장비 ID:",
                        "value": "{{deviceId}}"
                      },
                      {
                        "name": "상세 정보:",
                        "value": "{{message}}"
                      },
                      {
                        "name": "발생 시간:",
                        "value": "{{timestamp}}"
                      }
                    ]
                  }
                ],
                "potentialAction": [
                  {
                    "@type": "OpenUri",
                    "name": "대시보드 보기",
                    "targets": [
                      {
                        "os": "default",
                        "uri": "https://gpu-management.company.com"
                      }
                    ]
                  }
                ]
              }
```

### 4.3 PagerDuty 통합

#### PagerDuty 이벤트 API 설정
```python
# scripts/pagerduty-integration.py
import requests
import json
from datetime import datetime

class PagerDutyIntegration:
    def __init__(self, routing_key):
        self.routing_key = routing_key
        self.api_url = "https://events.pagerduty.com/v2/enqueue"
    
    def trigger_alert(self, severity, summary, source, component=None, group=None, custom_details=None):
        payload = {
            "routing_key": self.routing_key,
            "event_action": "trigger",
            "dedup_key": f"gpu-{source}-{component}-{datetime.now().strftime('%Y%m%d')}",
            "payload": {
                "summary": summary,
                "source": source,
                "severity": severity,
                "component": component or "gpu-management",
                "group": group or "gpu-cluster",
                "class": "gpu-hardware",
                "custom_details": custom_details or {}
            }
        }
        
        response = requests.post(self.api_url, 
                               data=json.dumps(payload),
                               headers={'Content-Type': 'application/json'})
        return response
    
    def resolve_alert(self, dedup_key):
        payload = {
            "routing_key": self.routing_key,
            "event_action": "resolve",
            "dedup_key": dedup_key
        }
        
        response = requests.post(self.api_url,
                               data=json.dumps(payload), 
                               headers={'Content-Type': 'application/json'})
        return response

# 사용 예시
pd = PagerDutyIntegration("YOUR_INTEGRATION_KEY")

# GPU 과열 알림
pd.trigger_alert(
    severity="warning",
    summary="GPU 과열 감지",
    source="gpu-worker-1-GPU-00",
    component="gpu-hardware",
    custom_details={
        "temperature": "87.5°C",
        "threshold": "85.0°C",
        "node": "gpu-worker-1"
    }
)
```

## 5. 외부 시스템 API 통합

### 5.1 Kubernetes Resource Operator 통합

#### Custom Resource Definition
```yaml
apiVersion: apiextensions.k8s.io/v1
kind: CustomResourceDefinition
metadata:
  name: gpuallocations.gpu.company.com
spec:
  group: gpu.company.com
  versions:
  - name: v1
    served: true
    storage: true
    schema:
      openAPIV3Schema:
        type: object
        properties:
          spec:
            type: object
            properties:
              workloadType:
                type: string
                enum: ["Training", "Inference", "Development"]
              requiredMemoryGb:
                type: integer
                minimum: 1
                maximum: 80
              useMig:
                type: boolean
              maxDurationHours:
                type: integer
                minimum: 1
                maximum: 168
              costLimit:
                type: number
                minimum: 0
          status:
            type: object
            properties:
              phase:
                type: string
                enum: ["Pending", "Allocated", "Failed", "Released"]
              allocatedResource:
                type: string
              allocationTime:
                type: string
                format: date-time
              message:
                type: string
  scope: Namespaced
  names:
    plural: gpuallocations
    singular: gpuallocation
    kind: GpuAllocation
    shortNames:
    - gpualloc
```

### 5.2 MLOps 플랫폼 통합

#### Kubeflow 통합
```yaml
# kubeflow-gpu-profile.yaml
apiVersion: kubeflow.org/v1
kind: Profile
metadata:
  name: gpu-training-profile
spec:
  owner:
    kind: User
    name: ml-engineer@company.com
  resourceQuotaSpec:
    hard:
      nvidia.com/gpu: "8"
      requests.nvidia.com/gpu: "8"
  plugins:
  - kind: WorkflowEngine
    spec:
      type: argo
  - kind: GPUManagement
    spec:
      endpoint: "http://k8s-gpu-management.gpu-management.svc.cluster.local:8080"
      autoAllocation: true
      costTracking: true

---
apiVersion: argoproj.io/v1alpha1
kind: WorkflowTemplate
metadata:
  name: gpu-training-template
  namespace: kubeflow
spec:
  entrypoint: training-pipeline
  templates:
  - name: training-pipeline
    steps:
    - - name: request-gpu
        template: gpu-allocation
    - - name: training
        template: model-training
        arguments:
          parameters:
          - name: gpu-allocation-id
            value: "{{steps.request-gpu.outputs.parameters.allocation-id}}"
    - - name: release-gpu
        template: gpu-release
        arguments:
          parameters:
          - name: gpu-allocation-id
            value: "{{steps.request-gpu.outputs.parameters.allocation-id}}"

  - name: gpu-allocation
    container:
      image: curlimages/curl:latest
      command: [sh, -c]
      args:
      - |
        ALLOCATION_RESPONSE=$(curl -X POST \
          http://k8s-gpu-management.gpu-management.svc.cluster.local:8080/api/v1/gpu/allocations \
          -H "Content-Type: application/json" \
          -d '{
            "namespace": "{{workflow.namespace}}",
            "podName": "{{workflow.name}}-training",
            "workloadType": "Training",
            "requiredMemoryGb": 40,
            "useMig": false
          }')
        echo $ALLOCATION_RESPONSE | jq -r '.data.allocationId' > /tmp/allocation-id
    outputs:
      parameters:
      - name: allocation-id
        valueFrom:
          path: /tmp/allocation-id
```

#### MLflow 통합
```python
# mlflow-gpu-integration.py
import mlflow
import requests
import os

class GPUTrackingMixin:
    def __init__(self):
        self.gpu_api_base = os.getenv('GPU_MANAGEMENT_API', 
                                     'http://k8s-gpu-management.gpu-management.svc.cluster.local:8080')
        self.allocation_id = None
    
    def request_gpu(self, memory_gb=20, use_mig=True, workload_type="Training"):
        """MLflow 실험 시작 시 GPU 할당 요청"""
        payload = {
            "namespace": os.getenv('NAMESPACE', 'default'),
            "podName": f"mlflow-{mlflow.active_run().info.run_id}",
            "workloadType": workload_type,
            "requiredMemoryGb": memory_gb,
            "useMig": use_mig
        }
        
        response = requests.post(f"{self.gpu_api_base}/api/v1/gpu/allocations", 
                               json=payload)
        if response.status_code == 200:
            result = response.json()
            self.allocation_id = result['data']['allocationId']
            
            # MLflow에 GPU 정보 로깅
            mlflow.log_param("gpu_allocation_id", self.allocation_id)
            mlflow.log_param("gpu_resource", result['data']['allocatedResource'])
            mlflow.log_param("gpu_cost_per_hour", result['data']['costPerHour'])
            
            return self.allocation_id
        else:
            raise Exception(f"GPU allocation failed: {response.text}")
    
    def release_gpu(self):
        """MLflow 실험 종료 시 GPU 해제"""
        if self.allocation_id:
            response = requests.delete(f"{self.gpu_api_base}/api/v1/gpu/allocations/{self.allocation_id}")
            if response.status_code == 200:
                mlflow.log_metric("gpu_released", 1)
            else:
                mlflow.log_metric("gpu_release_failed", 1)

# 사용 예시
class GPUMLflowExperiment(GPUTrackingMixin):
    def run_training(self):
        with mlflow.start_run():
            # GPU 할당
            allocation_id = self.request_gpu(memory_gb=40, use_mig=False)
            
            try:
                # 모델 훈련 코드
                model = self.train_model()
                
                # 모델 및 메트릭 로깅
                mlflow.log_metric("accuracy", model.accuracy)
                mlflow.sklearn.log_model(model, "model")
                
            finally:
                # GPU 해제
                self.release_gpu()
```

## 6. 보안 시스템 통합

### 6.1 LDAP/Active Directory 통합

#### LDAP 인증 설정
```yaml
spring:
  ldap:
    urls: ldap://ldap.company.com:389
    base: dc=company,dc=com
    username: cn=admin,dc=company,dc=com
    password: ${LDAP_PASSWORD}
    
  security:
    ldap:
      authentication:
        user-search-base: ou=people
        user-search-filter: (uid={0})
        group-search-base: ou=groups
        group-search-filter: (member={0})
        group-role-attribute: cn
        role-prefix: ROLE_

gpu:
  management:
    security:
      ldap:
        enabled: true
        group-mappings:
          "GPU-Admins": "ADMIN"
          "ML-Engineers": "USER"
          "DevOps": "OPERATOR"
          "Finance": "VIEWER"
```

#### RBAC 정책 매핑
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/v1/gpu/devices/**").hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers("/api/v1/gpu/allocations/**").hasAnyRole("ADMIN", "USER", "OPERATOR")
                .requestMatchers("/api/v1/gpu/cost/**").hasAnyRole("ADMIN", "VIEWER", "FINANCE")
                .requestMatchers("/api/v1/gpu/overview").hasAnyRole("ADMIN", "USER", "OPERATOR", "VIEWER")
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .ldapAuthentication(ldap -> ldap
                .userDnPatterns("uid={0},ou=people")
                .groupSearchBase("ou=groups")
                .contextSource(contextSource())
            );
        
        return http.build();
    }
}
```

### 6.2 OAuth 2.0 / OpenID Connect 통합

#### Keycloak 통합
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://keycloak.company.com/auth/realms/gpu-management
          jwk-set-uri: https://keycloak.company.com/auth/realms/gpu-management/protocol/openid-connect/certs

gpu:
  management:
    security:
      oauth2:
        enabled: true
        client-id: gpu-management
        client-secret: ${OAUTH2_CLIENT_SECRET}
        scopes: openid,profile,email,gpu-management
        role-claim: realm_access.roles
```

## 7. 백업 시스템 통합

### 7.1 Velero 백업 통합

#### Velero 백업 스케줄
```yaml
apiVersion: velero.io/v1
kind: Schedule
metadata:
  name: gpu-management-backup
  namespace: velero
spec:
  schedule: "0 2 * * *"  # 매일 새벽 2시
  template:
    includedNamespaces:
    - gpu-management
    includedResources:
    - configmaps
    - secrets
    - persistentvolumeclaims
    - persistentvolumes
    storageLocation: default
    volumeSnapshotLocations:
    - default
    ttl: 720h0m0s  # 30일 보관
    hooks:
      resources:
      - name: gpu-management-db-backup
        includedNamespaces:
        - gpu-management
        labelSelector:
          matchLabels:
            app: oracle-db
        pre:
        - exec:
            container: oracle-db
            command:
            - /bin/bash
            - -c
            - |
              # Oracle DB 백업 스크립트
              expdp gpu_admin/password DIRECTORY=backup_dir \
                DUMPFILE=gpu_management_%date%.dmp \
                SCHEMAS=gpu_admin COMPRESSION=all
```

#### GPU 메트릭 데이터 백업
```bash
#!/bin/bash
# scripts/backup-gpu-metrics.sh

# 환경 변수
NAMESPACE="gpu-management"
DB_POD=$(kubectl get pods -n $NAMESPACE -l app=oracle-db -o jsonpath='{.items[0].metadata.name}')
BACKUP_DIR="/backup/gpu-metrics"
DATE=$(date +%Y%m%d_%H%M%S)

# 백업 디렉토리 생성
mkdir -p $BACKUP_DIR

# GPU 메트릭 데이터 백업
echo "Starting GPU metrics backup..."

# Oracle DB 백업
kubectl exec -n $NAMESPACE $DB_POD -- expdp gpu_admin/password \
  DIRECTORY=backup_dir \
  DUMPFILE=gpu_metrics_$DATE.dmp \
  TABLES=gpu_usage_metrics,gpu_allocations \
  COMPRESSION=all

# 백업 파일을 로컬로 복사
kubectl cp $NAMESPACE/$DB_POB:/backup/gpu_metrics_$DATE.dmp \
  $BACKUP_DIR/gpu_metrics_$DATE.dmp

# S3에 업로드 (선택사항)
if [ "$UPLOAD_TO_S3" = "true" ]; then
  aws s3 cp $BACKUP_DIR/gpu_metrics_$DATE.dmp \
    s3://$S3_BUCKET/gpu-management/metrics/
fi

echo "GPU metrics backup completed: $BACKUP_DIR/gpu_metrics_$DATE.dmp"
```

### 7.2 데이터베이스 백업 자동화

#### Oracle 백업 CronJob
```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: gpu-db-backup
  namespace: gpu-management
spec:
  schedule: "0 1 * * *"  # 매일 새벽 1시
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: backup
            image: oracle/database:19.3.0-ee
            env:
            - name: ORACLE_SID
              value: "ORCL"
            - name: ORACLE_PWD
              valueFrom:
                secretKeyRef:
                  name: oracle-secret
                  key: password
            command:
            - /bin/bash
            - -c
            - |
              # 데이터베이스 백업 수행
              DATE=$(date +%Y%m%d_%H%M%S)
              
              # 전체 스키마 백업
              expdp gpu_admin/${ORACLE_PWD} \
                DIRECTORY=backup_dir \
                DUMPFILE=full_backup_${DATE}.dmp \
                SCHEMAS=gpu_admin \
                COMPRESSION=all \
                LOGFILE=backup_${DATE}.log
              
              # 증분 백업 (메트릭 테이블만)
              expdp gpu_admin/${ORACLE_PWD} \
                DIRECTORY=backup_dir \
                DUMPFILE=metrics_incr_${DATE}.dmp \
                TABLES=gpu_usage_metrics \
                QUERY="gpu_usage_metrics:\"WHERE created_at >= SYSDATE - 1\"" \
                COMPRESSION=all
              
              # 백업 파일 정리 (30일 이상 된 파일 삭제)
              find /backup -name "*.dmp" -mtime +30 -delete
            volumeMounts:
            - name: backup-storage
              mountPath: /backup
          volumes:
          - name: backup-storage
            persistentVolumeClaim:
              claimName: backup-pvc
          restartPolicy: OnFailure
```

## 8. API 통합 가이드

### 8.1 REST API 클라이언트 예시

#### Python 클라이언트
```python
# gpu_client.py
import requests
import json
from datetime import datetime
from typing import Dict, List, Optional

class GPUManagementClient:
    def __init__(self, base_url: str, api_key: Optional[str] = None):
        self.base_url = base_url.rstrip('/')
        self.session = requests.Session()
        if api_key:
            self.session.headers.update({'Authorization': f'Bearer {api_key}'})
    
    def get_gpu_devices(self, node_name: Optional[str] = None) -> List[Dict]:
        """GPU 장비 목록 조회"""
        params = {'nodeName': node_name} if node_name else {}
        response = self.session.get(f'{self.base_url}/api/v1/gpu/devices', params=params)
        response.raise_for_status()
        return response.json()
    
    def allocate_gpu(self, allocation_request: Dict) -> Dict:
        """GPU 리소스 할당"""
        response = self.session.post(
            f'{self.base_url}/api/v1/gpu/allocations',
            json=allocation_request
        )
        response.raise_for_status()
        return response.json()
    
    def release_gpu(self, allocation_id: str) -> None:
        """GPU 리소스 해제"""
        response = self.session.delete(f'{self.base_url}/api/v1/gpu/allocations/{allocation_id}')
        response.raise_for_status()
    
    def get_cluster_overview(self) -> Dict:
        """클러스터 개요 조회"""
        response = self.session.get(f'{self.base_url}/api/v1/gpu/overview')
        response.raise_for_status()
        return response.json()
    
    def get_cost_analysis(self, days: int = 30) -> Dict:
        """비용 분석 조회"""
        response = self.session.get(
            f'{self.base_url}/api/v1/gpu/cost/analysis',
            params={'days': days}
        )
        response.raise_for_status()
        return response.json()

# 사용 예시
client = GPUManagementClient('https://gpu-management.company.com')

# GPU 장비 조회
devices = client.get_gpu_devices()
print(f"Total GPU devices: {len(devices)}")

# GPU 할당
allocation_request = {
    "namespace": "ml-training",
    "podName": "pytorch-training-job",
    "workloadType": "Training",
    "requiredMemoryGb": 32,
    "useMig": False,
    "userId": "data-scientist@company.com",
    "teamId": "ml-team"
}

allocation = client.allocate_gpu(allocation_request)
print(f"Allocated GPU: {allocation['allocatedResource']}")

# 작업 완료 후 해제
# client.release_gpu(allocation['allocationId'])
```

#### Java 클라이언트
```java
// GPUManagementClient.java
@Component
public class GPUManagementClient {
    
    private final RestTemplate restTemplate;
    private final String baseUrl;
    
    public GPUManagementClient(RestTemplate restTemplate, 
                              @Value("${gpu.management.api.url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }
    
    public List<GpuDeviceInfo> getGpuDevices(String nodeName) {
        String url = baseUrl + "/api/v1/gpu/devices";
        if (nodeName != null) {
            url += "?nodeName=" + nodeName;
        }
        
        ResponseEntity<List<GpuDeviceInfo>> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<List<GpuDeviceInfo>>() {}
        );
        
        return response.getBody();
    }
    
    public GpuAllocationInfo allocateGpu(GpuAllocationRequest request) {
        String url = baseUrl + "/api/v1/gpu/allocations";
        
        HttpEntity<GpuAllocationRequest> entity = new HttpEntity<>(request);
        ResponseEntity<GpuAllocationInfo> response = restTemplate.exchange(
            url,
            HttpMethod.POST,
            entity,
            GpuAllocationInfo.class
        );
        
        return response.getBody();
    }
    
    public void releaseGpu(String allocationId) {
        String url = baseUrl + "/api/v1/gpu/allocations/" + allocationId;
        restTemplate.delete(url);
    }
    
    public GpuClusterOverview getClusterOverview() {
        String url = baseUrl + "/api/v1/gpu/overview";
        
        ResponseEntity<GpuClusterOverview> response = restTemplate.getForEntity(
            url, GpuClusterOverview.class
        );
        
        return response.getBody();
    }
}

// 사용 예시
@Service
public class MLJobService {
    
    private final GPUManagementClient gpuClient;
    
    public MLJobService(GPUManagementClient gpuClient) {
        this.gpuClient = gpuClient;
    }
    
    public String startTrainingJob(TrainingJobRequest jobRequest) {
        // GPU 할당 요청
        GpuAllocationRequest allocationRequest = GpuAllocationRequest.builder()
            .namespace("ml-training")
            .podName("training-" + UUID.randomUUID().toString().substring(0, 8))
            .workloadType("Training")
            .requiredMemoryGb(jobRequest.getRequiredMemoryGb())
            .useMig(jobRequest.isUseMig())
            .userId(jobRequest.getUserId())
            .teamId(jobRequest.getTeamId())
            .build();
        
        GpuAllocationInfo allocation = gpuClient.allocateGpu(allocationRequest);
        
        // 실제 ML 작업 실행 로직...
        
        return allocation.getAllocationId();
    }
    
    public void stopTrainingJob(String allocationId) {
        // 작업 정리 로직...
        
        // GPU 해제
        gpuClient.releaseGpu(allocationId);
    }
}
```

### 8.2 Webhook 통합

#### GPU 이벤트 Webhook 설정
```yaml
# application-gpu.yml에 추가
gpu:
  management:
    webhooks:
      enabled: true
      endpoints:
        - name: "slack-notifications"
          url: "${SLACK_WEBHOOK_URL}"
          events: ["allocation.created", "allocation.released", "device.overheating"]
          headers:
            Content-Type: "application/json"
        - name: "monitoring-system"
          url: "${MONITORING_WEBHOOK_URL}"
          events: ["device.failure", "allocation.failed"]
          headers:
            Authorization: "Bearer ${MONITORING_API_KEY}"
      retry:
        maxAttempts: 3
        backoffMs: 1000
```

#### Webhook 처리 코드
```java
// WebhookService.java
@Service
@Slf4j
public class WebhookService {
    
    private final RestTemplate restTemplate;
    private final WebhookProperties webhookProperties;
    
    @Async
    public void sendWebhook(String eventType, Object eventData) {
        List<WebhookEndpoint> endpoints = webhookProperties.getEndpointsForEvent(eventType);
        
        for (WebhookEndpoint endpoint : endpoints) {
            try {
                sendWebhookToEndpoint(endpoint, eventType, eventData);
            } catch (Exception e) {
                log.error("Failed to send webhook to {}: {}", endpoint.getUrl(), e.getMessage());
                // 재시도 로직...
            }
        }
    }
    
    private void sendWebhookToEndpoint(WebhookEndpoint endpoint, String eventType, Object eventData) {
        WebhookPayload payload = WebhookPayload.builder()
            .eventType(eventType)
            .timestamp(LocalDateTime.now())
            .data(eventData)
            .source("gpu-management")
            .build();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        endpoint.getHeaders().forEach(headers::add);
        
        HttpEntity<WebhookPayload> entity = new HttpEntity<>(payload, headers);
        
        ResponseEntity<String> response = restTemplate.postForEntity(
            endpoint.getUrl(), 
            entity, 
            String.class
        );
        
        if (response.getStatusCode().is2xxSuccessful()) {
            log.debug("Webhook sent successfully to {}", endpoint.getUrl());
        } else {
            throw new RuntimeException("Webhook failed with status: " + response.getStatusCode());
        }
    }
}
```

## 9. 문제 해결

### 9.1 일반적인 통합 문제

#### 네트워크 연결 문제
```bash
# 서비스 연결 테스트
kubectl run test-pod --image=curlimages/curl -it --rm -- \
  curl -v http://k8s-gpu-management.gpu-management.svc.cluster.local:8080/actuator/health

# DNS 해결 확인
kubectl run test-pod --image=busybox -it --rm -- \
  nslookup k8s-gpu-management.gpu-management.svc.cluster.local

# 포트 접근 확인
kubectl run test-pod --image=nicolaka/netshoot -it --rm -- \
  nc -zv k8s-gpu-management.gpu-management.svc.cluster.local 8080
```

#### API 인증 문제
```bash
# JWT 토큰 검증
curl -H "Authorization: Bearer $TOKEN" \
  https://gpu-management.company.com/api/v1/gpu/devices

# RBAC 권한 확인
kubectl auth can-i create gpuallocations --as=system:serviceaccount:ml-training:ml-service

# 서비스 계정 토큰 확인
kubectl get secret $(kubectl get sa ml-service -o jsonpath='{.secrets[0].name}') -o jsonpath='{.data.token}' | base64 -d
```

### 9.2 성능 최적화

#### API 응답 시간 개선
```yaml
# application-gpu.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50
      minimum-idle: 20
  jpa:
    properties:
      hibernate:
        cache:
          use_query_cache: true
        jdbc:
          batch_size: 100

gpu:
  management:
    cache:
      enabled: true
      ttl-seconds: 30
      max-size: 10000
```

#### 메트릭 수집 최적화
```yaml
gpu:
  management:
    metrics:
      collection-interval: 15s  # 더 빠른 수집
      batch-size: 500          # 더 큰 배치
      async-processing: true   # 비동기 처리
```

## 10. 보안 고려사항

### 10.1 API 보안

#### API 키 관리
```yaml
# Secret 생성
apiVersion: v1
kind: Secret
metadata:
  name: gpu-management-api-keys
  namespace: gpu-management
type: Opaque
data:
  ml-team-api-key: <base64-encoded-key>
  monitoring-api-key: <base64-encoded-key>
```

#### 네트워크 정책
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: gpu-management-netpol
  namespace: gpu-management
spec:
  podSelector:
    matchLabels:
      app: k8s-gpu-management
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: ml-training
    - namespaceSelector:
        matchLabels:
          name: monitoring
    ports:
    - protocol: TCP
      port: 8080
  egress:
  - to:
    - namespaceSelector:
        matchLabels:
          name: database
    ports:
    - protocol: TCP
      port: 1521
```

### 10.2 데이터 암호화

#### 저장 데이터 암호화
```yaml
# Oracle TDE 설정 (application-gpu.yml)
spring:
  datasource:
    url: jdbc:oracle:thin:@//oracle-db:1521/ORCL
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    properties:
      oracle.net.encryption_client: REQUIRED
      oracle.net.encryption_types_client: AES256
      oracle.net.crypto_checksum_client: REQUIRED
      oracle.net.crypto_checksum_types_client: SHA256
```

#### 전송 데이터 암호화
```yaml
# Ingress TLS 설정
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: gpu-management-ingress
  namespace: gpu-management
  annotations:
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/force-ssl-redirect: "true"
spec:
  tls:
  - hosts:
    - gpu-management.company.com
    secretName: gpu-management-tls
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

이 통합 가이드를 통해 K8s GPU Management 시스템을 다양한 외부 시스템과 효과적으로 연동할 수 있습니다.