# K8s GPU Management 설정 가이드

## 개요

이 문서는 Kubernetes GPU 관리 시스템의 상세한 설정 방법을 설명합니다.

## 1. 애플리케이션 설정

### 1.1 기본 설정 파일

#### application.yml
```yaml
# 기본 애플리케이션 설정
server:
  port: 8080
  servlet:
    context-path: /k8s-monitor
  compression:
    enabled: true
    mime-types: text/html,text/xml,text/plain,text/css,application/json
    min-response-size: 1024

spring:
  application:
    name: k8s-resource-monitor
  
  # 프로파일 설정
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:development}
    
  # 데이터베이스 설정
  datasource:
    url: ${DB_URL:jdbc:h2:mem:k8s_monitor}
    driver-class-name: ${DB_DRIVER:org.h2.Driver}
    username: ${DB_USERNAME:sa}
    password: ${DB_PASSWORD:password}
    hikari:
      maximum-pool-size: ${DB_POOL_MAX:10}
      minimum-idle: ${DB_POOL_MIN:5}
      connection-timeout: ${DB_TIMEOUT:20000}
      idle-timeout: 300000
      max-lifetime: 1200000
      
  # JPA 설정
  jpa:
    database-platform: ${JPA_DIALECT:org.hibernate.dialect.H2Dialect}
    hibernate:
      ddl-auto: ${DDL_AUTO:create-drop}
      naming:
        physical-strategy: org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl
    show-sql: ${SHOW_SQL:false}
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true
        generate_statistics: ${HIBERNATE_STATS:false}
        
  # Jackson JSON 설정
  jackson:
    serialization:
      write-dates-as-timestamps: false
      indent-output: true
    time-zone: ${TIMEZONE:Asia/Seoul}
    date-format: yyyy-MM-dd HH:mm:ss

# Spring Boot Actuator 설정
management:
  endpoints:
    web:
      exposure:
        include: ${ACTUATOR_ENDPOINTS:health,info,metrics,prometheus}
      base-path: /actuator
  endpoint:
    health:
      show-details: ${HEALTH_DETAILS:always}
      show-components: always
    metrics:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: ${PROMETHEUS_ENABLED:true}
    tags:
      application: ${spring.application.name}
      environment: ${spring.profiles.active}
      cluster: ${CLUSTER_NAME:default}

# 로깅 설정
logging:
  level:
    root: ${LOG_LEVEL:INFO}
    com.k8s.monitor: ${APP_LOG_LEVEL:DEBUG}
    io.kubernetes.client: ${K8S_CLIENT_LOG_LEVEL:WARN}
    org.springframework.web: ${WEB_LOG_LEVEL:INFO}
    org.hibernate.SQL: ${SQL_LOG_LEVEL:WARN}
    org.hibernate.type.descriptor.sql.BasicBinder: ${SQL_BIND_LOG_LEVEL:WARN}
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: ${LOG_FILE:logs/k8s-monitor.log}
    max-size: ${LOG_MAX_SIZE:10MB}
    max-history: ${LOG_MAX_HISTORY:30}
```

### 1.2 GPU 관리 전용 설정

#### application-gpu.yml
```yaml
spring:
  config:
    activate:
      on-profile: gpu-management

# GPU 관리 핵심 설정
gpu:
  management:
    enabled: ${GPU_MANAGEMENT_ENABLED:true}
    
    # 지원 GPU 모델 설정
    supported-models: ${GPU_SUPPORTED_MODELS:RTX4090,A100_40GB,A100_80GB,H100_80GB}
    
    # MIG 관리 설정
    mig:
      enabled: ${GPU_MIG_ENABLED:true}
      supported-models: ${GPU_MIG_MODELS:A100_40GB,A100_80GB,H100_80GB}
      auto-cleanup: ${GPU_MIG_AUTO_CLEANUP:true}
      cleanup-interval: ${GPU_MIG_CLEANUP_INTERVAL:0 0 2 * * *}
      unused-threshold-days: ${GPU_MIG_UNUSED_DAYS:7}
    
    # 메트릭 수집 설정
    metrics:
      collection-interval: ${GPU_METRICS_INTERVAL:30s}
      retention-days: ${GPU_METRICS_RETENTION:30}
      batch-size: ${GPU_METRICS_BATCH_SIZE:100}
      
      # NVIDIA SMI 설정
      nvidia-smi:
        enabled: ${GPU_NVIDIA_SMI_ENABLED:true}
        path: ${GPU_NVIDIA_SMI_PATH:/usr/bin/nvidia-smi}
        timeout: ${GPU_NVIDIA_SMI_TIMEOUT:10s}
        retry-attempts: ${GPU_NVIDIA_SMI_RETRY:3}
        
      # NVML 라이브러리 설정 (선택사항)
      nvml:
        enabled: ${GPU_NVML_ENABLED:false}
        library-path: ${GPU_NVML_PATH:/usr/local/cuda/lib64/libnvidia-ml.so}
    
    # 할당 관리 설정
    allocation:
      auto-expire: ${GPU_ALLOCATION_AUTO_EXPIRE:true}
      expire-check-interval: ${GPU_ALLOCATION_CHECK_INTERVAL:5m}
      default-duration-hours: ${GPU_ALLOCATION_DEFAULT_HOURS:24}
      max-duration-hours: ${GPU_ALLOCATION_MAX_HOURS:168}
      cost-tracking: ${GPU_COST_TRACKING:true}
      
    # 비용 계산 설정
    cost:
      enabled: ${GPU_COST_ENABLED:true}
      currency: ${GPU_COST_CURRENCY:USD}
      default-rates:
        GTX1080: ${GPU_COST_GTX1080:0.5}
        RTX4090: ${GPU_COST_RTX4090:2.0}
        A100_40GB: ${GPU_COST_A100_40GB:4.0}
        A100_80GB: ${GPU_COST_A100_80GB:6.0}
        H100_80GB: ${GPU_COST_H100_80GB:8.0}
      mig-discount: ${GPU_COST_MIG_DISCOUNT:0.7}
      
    # 알람 설정
    alerts:
      enabled: ${GPU_ALERTS_ENABLED:true}
      temperature-threshold: ${GPU_ALERT_TEMP:85.0}
      utilization-threshold: ${GPU_ALERT_UTIL:90.0}
      memory-threshold: ${GPU_ALERT_MEM:95.0}
      power-threshold: ${GPU_ALERT_POWER:400.0}
      
      # 알림 채널 설정
      notification:
        email:
          enabled: ${GPU_ALERT_EMAIL_ENABLED:false}
          smtp-host: ${SMTP_HOST:localhost}
          smtp-port: ${SMTP_PORT:587}
          username: ${SMTP_USERNAME:}
          password: ${SMTP_PASSWORD:}
          from: ${SMTP_FROM:gpu-alerts@company.com}
          recipients: ${GPU_ALERT_EMAIL_RECIPIENTS:}
          
        slack:
          enabled: ${GPU_ALERT_SLACK_ENABLED:false}
          webhook-url: ${SLACK_WEBHOOK_URL:}
          channel: ${SLACK_CHANNEL:#gpu-alerts}
          username: ${SLACK_USERNAME:GPU Monitor}
          
        webhook:
          enabled: ${GPU_ALERT_WEBHOOK_ENABLED:false}
          url: ${WEBHOOK_URL:}
          secret: ${WEBHOOK_SECRET:}
          
    # 최적화 설정
    optimization:
      enabled: ${GPU_OPTIMIZATION_ENABLED:true}
      auto-optimization: ${GPU_AUTO_OPTIMIZATION:false}
      optimization-interval: ${GPU_OPTIMIZATION_INTERVAL:0 0 3 * * *}
      strategies: ${GPU_OPTIMIZATION_STRATEGIES:UNUSED_MIG_CLEANUP,OVERPROVISIONED_DETECTION,COST_OPTIMIZATION}
      
    # 보안 설정
    security:
      api-key-enabled: ${GPU_API_KEY_ENABLED:false}
      jwt-enabled: ${GPU_JWT_ENABLED:false}
      rbac-enabled: ${GPU_RBAC_ENABLED:true}
      
    # 캐시 설정
    cache:
      enabled: ${GPU_CACHE_ENABLED:true}
      ttl-seconds: ${GPU_CACHE_TTL:300}
      max-size: ${GPU_CACHE_MAX_SIZE:1000}
      
    # 배치 처리 설정
    batch:
      enabled: ${GPU_BATCH_ENABLED:true}
      pool-size: ${GPU_BATCH_POOL_SIZE:5}
      queue-capacity: ${GPU_BATCH_QUEUE_CAPACITY:100}
```

### 1.3 환경별 설정

#### 개발 환경 (application-development.yml)
```yaml
spring:
  config:
    activate:
      on-profile: development

  # H2 인메모리 데이터베이스
  datasource:
    url: jdbc:h2:mem:gpu_management_dev
    driver-class-name: org.h2.Driver
    username: sa
    password: password

  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true

  h2:
    console:
      enabled: true
      path: /h2-console

gpu:
  management:
    metrics:
      collection-interval: 10s
      nvidia-smi:
        enabled: false # 개발 환경에서는 모의 데이터 사용
    allocation:
      default-duration-hours: 1
    cost:
      default-rates:
        RTX4090: 0.1 # 개발 환경용 저렴한 요금

logging:
  level:
    com.k8s.monitor: DEBUG
    org.hibernate.SQL: DEBUG
```

#### 운영 환경 (application-production.yml)
```yaml
spring:
  config:
    activate:
      on-profile: production

  # Oracle 데이터베이스
  datasource:
    url: jdbc:oracle:thin:@${DB_HOST}:${DB_PORT}:${DB_SERVICE}
    driver-class-name: oracle.jdbc.OracleDriver
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 50
      minimum-idle: 20
      leak-detection-threshold: 60000

  jpa:
    database-platform: org.hibernate.dialect.Oracle12cDialect
    hibernate:
      ddl-auto: validate
    show-sql: false

gpu:
  management:
    metrics:
      collection-interval: 30s
      retention-days: 90
      batch-size: 500
    allocation:
      auto-expire: true
      default-duration-hours: 24
    alerts:
      enabled: true
      notification:
        email:
          enabled: true
          recipients: gpu-admin@company.com,ops-team@company.com
        slack:
          enabled: true
          webhook-url: "${SLACK_WEBHOOK_URL}"

logging:
  level:
    root: WARN
    com.k8s.monitor: INFO
  file:
    name: /var/log/k8s-monitor/gpu-management.log
    max-size: 100MB
    max-history: 30
```

## 2. 데이터베이스 설정

### 2.1 Oracle Database 설정

#### 연결 설정
```yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@//oracle-host:1521/ORCL
    driver-class-name: oracle.jdbc.OracleDriver
    username: gpu_admin
    password: ${DB_PASSWORD}
    
    # 연결 풀 최적화
    hikari:
      maximum-pool-size: 20
      minimum-idle: 10
      connection-timeout: 20000
      idle-timeout: 300000
      max-lifetime: 1200000
      validation-timeout: 5000
      leak-detection-threshold: 60000
      
  jpa:
    database-platform: org.hibernate.dialect.Oracle12cDialect
    properties:
      hibernate:
        jdbc:
          batch_size: 20
          order_inserts: true
          order_updates: true
        cache:
          use_second_level_cache: true
          use_query_cache: true
```

#### 테이블스페이스 설정
```sql
-- 데이터 테이블스페이스
CREATE TABLESPACE gpu_data
DATAFILE '/opt/oracle/oradata/ORCL/gpu_data01.dbf' SIZE 2G
AUTOEXTEND ON NEXT 100M MAXSIZE 10G;

-- 인덱스 테이블스페이스
CREATE TABLESPACE gpu_index
DATAFILE '/opt/oracle/oradata/ORCL/gpu_index01.dbf' SIZE 1G
AUTOEXTEND ON NEXT 50M MAXSIZE 5G;

-- 사용자 설정
ALTER USER gpu_admin DEFAULT TABLESPACE gpu_data;
ALTER USER gpu_admin TEMPORARY TABLESPACE temp;
```

### 2.2 PostgreSQL 설정 (대안)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres-host:5432/gpu_management
    driver-class-name: org.postgresql.Driver
    username: gpu_admin
    password: ${DB_PASSWORD}
    
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQL12Dialect
```

## 3. Kubernetes 설정

### 3.1 ConfigMap 설정

#### gpu-management-config.yaml
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: gpu-management-config
  namespace: gpu-management
data:
  application.yml: |
    spring:
      profiles:
        active: production,gpu-management
    
    gpu:
      management:
        enabled: true
        metrics:
          collection-interval: 30s
        alerts:
          enabled: true
        cost:
          enabled: true
          
  logback-spring.xml: |
    <?xml version="1.0" encoding="UTF-8"?>
    <configuration>
      <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
          <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
      </appender>
      
      <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>/var/log/gpu-management.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
          <fileNamePattern>/var/log/gpu-management.%d{yyyy-MM-dd}.%i.log</fileNamePattern>
          <maxFileSize>100MB</maxFileSize>
          <maxHistory>30</maxHistory>
          <totalSizeCap>3GB</totalSizeCap>
        </rollingPolicy>
        <encoder>
          <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
      </appender>
      
      <logger name="com.k8s.monitor" level="INFO"/>
      <logger name="org.hibernate.SQL" level="WARN"/>
      
      <root level="INFO">
        <appender-ref ref="STDOUT"/>
        <appender-ref ref="FILE"/>
      </root>
    </configuration>
```

### 3.2 Secret 설정

#### gpu-management-secrets.yaml
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: gpu-management-secrets
  namespace: gpu-management
type: Opaque
data:
  # Base64로 인코딩된 값들
  db-password: <base64-encoded-password>
  slack-webhook-url: <base64-encoded-webhook-url>
  smtp-password: <base64-encoded-smtp-password>
  api-key: <base64-encoded-api-key>
  jwt-secret: <base64-encoded-jwt-secret>
```

### 3.3 Deployment 설정

#### deployment.yaml
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: k8s-gpu-management
  namespace: gpu-management
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 1
      maxSurge: 1
  selector:
    matchLabels:
      app: k8s-gpu-management
  template:
    metadata:
      labels:
        app: k8s-gpu-management
    spec:
      serviceAccountName: gpu-management
      containers:
      - name: app
        image: k8s-gpu-management:latest
        ports:
        - containerPort: 8080
          name: http
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "production,gpu-management"
        - name: DB_HOST
          value: "oracle-db.default.svc.cluster.local"
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: gpu-management-secrets
              key: db-password
        - name: SLACK_WEBHOOK_URL
          valueFrom:
            secretKeyRef:
              name: gpu-management-secrets
              key: slack-webhook-url
        volumeMounts:
        - name: config
          mountPath: /app/config
        - name: logs
          mountPath: /var/log
        resources:
          requests:
            memory: "2Gi"
            cpu: "500m"
          limits:
            memory: "4Gi"
            cpu: "2000m"
        livenessProbe:
          httpGet:
            path: /k8s-monitor/actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /k8s-monitor/actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
      volumes:
      - name: config
        configMap:
          name: gpu-management-config
      - name: logs
        emptyDir: {}
```

## 4. 모니터링 설정

### 4.1 Prometheus 설정

#### ServiceMonitor
```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: gpu-management-monitor
  namespace: gpu-management
spec:
  selector:
    matchLabels:
      app: k8s-gpu-management
  endpoints:
  - port: http
    path: /k8s-monitor/actuator/prometheus
    interval: 30s
    scrapeTimeout: 10s
```

#### PrometheusRule
```yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: gpu-management-alerts
  namespace: gpu-management
spec:
  groups:
  - name: gpu.rules
    rules:
    - alert: GPUHighTemperature
      expr: gpu_temperature_celsius > 85
      for: 5m
      labels:
        severity: warning
      annotations:
        summary: "GPU {{ $labels.device_id }} temperature is high"
        description: "GPU {{ $labels.device_id }} temperature is {{ $value }}°C"
        
    - alert: GPUHighUtilization
      expr: gpu_utilization_percent > 95
      for: 10m
      labels:
        severity: warning
      annotations:
        summary: "GPU {{ $labels.device_id }} utilization is high"
        
    - alert: GPUAllocationFailed
      expr: increase(gpu_allocation_failures_total[5m]) > 0
      labels:
        severity: critical
      annotations:
        summary: "GPU allocation failures detected"
```

### 4.2 Grafana 대시보드 설정

#### datasources.yml
```yaml
apiVersion: 1
datasources:
- name: Prometheus
  type: prometheus
  url: http://prometheus:9090
  access: proxy
  isDefault: true
```

#### 대시보드 프로비저닝
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: grafana-gpu-dashboard
  namespace: monitoring
data:
  gpu-overview.json: |
    {
      "dashboard": {
        "title": "GPU Management Overview",
        "panels": [
          {
            "title": "GPU Utilization",
            "type": "graph",
            "targets": [
              {
                "expr": "avg(gpu_utilization_percent) by (device_id)"
              }
            ]
          }
        ]
      }
    }
```

## 5. 보안 설정

### 5.1 RBAC 설정

#### ClusterRole
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
- apiGroups: [""]
  resources: ["events"]
  verbs: ["get", "list", "watch"]
```

#### ServiceAccount 및 Binding
```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: gpu-management
  namespace: gpu-management
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: gpu-management-binding
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: gpu-management-reader
subjects:
- kind: ServiceAccount
  name: gpu-management
  namespace: gpu-management
```

### 5.2 네트워크 정책

#### NetworkPolicy
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
          name: monitoring
    - namespaceSelector:
        matchLabels:
          name: ingress-nginx
    ports:
    - protocol: TCP
      port: 8080
  egress:
  - to:
    - namespaceSelector:
        matchLabels:
          name: default
    ports:
    - protocol: TCP
      port: 1521 # Oracle DB
  - to: []
    ports:
    - protocol: TCP
      port: 443 # HTTPS
    - protocol: TCP
      port: 53  # DNS
    - protocol: UDP
      port: 53  # DNS
```

### 5.3 Pod Security Policy

```yaml
apiVersion: policy/v1beta1
kind: PodSecurityPolicy
metadata:
  name: gpu-management-psp
spec:
  privileged: false
  allowPrivilegeEscalation: false
  requiredDropCapabilities:
    - ALL
  volumes:
    - 'configMap'
    - 'emptyDir'
    - 'projected'
    - 'secret'
    - 'downwardAPI'
    - 'persistentVolumeClaim'
  runAsUser:
    rule: 'MustRunAsNonRoot'
  seLinux:
    rule: 'RunAsAny'
  fsGroup:
    rule: 'RunAsAny'
```

## 6. 알림 설정

### 6.1 Slack 알림 설정

```yaml
gpu:
  management:
    alerts:
      notification:
        slack:
          enabled: true
          webhook-url: "https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXX"
          channel: "#gpu-alerts"
          username: "GPU Monitor"
          templates:
            overheating: |
              🌡️ *GPU 과열 알림*
              • 장비: {{deviceId}}
              • 온도: {{temperature}}°C
              • 임계값: {{threshold}}°C
              • 시간: {{timestamp}}
            allocation-failed: |
              ❌ *GPU 할당 실패*
              • Pod: {{namespace}}/{{podName}}
              • 사유: {{reason}}
              • 시간: {{timestamp}}
```

### 6.2 이메일 알림 설정

```yaml
gpu:
  management:
    alerts:
      notification:
        email:
          enabled: true
          smtp:
            host: smtp.company.com
            port: 587
            username: gpu-alerts@company.com
            password: ${SMTP_PASSWORD}
            ssl: true
          from: "GPU Management <gpu-alerts@company.com>"
          recipients:
            - gpu-admin@company.com
            - devops-team@company.com
          templates:
            subject-prefix: "[GPU Alert]"
            html-template: |
              <html>
              <body>
                <h2>GPU 관리 시스템 알림</h2>
                <p><strong>알림 유형:</strong> {{alertType}}</p>
                <p><strong>장비 ID:</strong> {{deviceId}}</p>
                <p><strong>상세 정보:</strong> {{message}}</p>
                <p><strong>발생 시간:</strong> {{timestamp}}</p>
              </body>
              </html>
```

### 6.3 웹훅 알림 설정

```yaml
gpu:
  management:
    alerts:
      notification:
        webhook:
          enabled: true
          url: "https://your-webhook-endpoint.com/gpu-alerts"
          secret: "${WEBHOOK_SECRET}"
          timeout: 30s
          retry-attempts: 3
          headers:
            Content-Type: "application/json"
            Authorization: "Bearer ${WEBHOOK_TOKEN}"
```

## 7. 성능 튜닝 설정

### 7.1 JVM 튜닝

```yaml
env:
- name: JAVA_OPTS
  value: |
    -Xms2g
    -Xmx4g
    -XX:+UseG1GC
    -XX:MaxGCPauseMillis=200
    -XX:+DisableExplicitGC
    -XX:+HeapDumpOnOutOfMemoryError
    -XX:HeapDumpPath=/var/log/heapdump.hprof
    -Djava.security.egd=file:/dev/./urandom
    -Dspring.backgroundpreinitializer.ignore=true
```

### 7.2 데이터베이스 성능 설정

```yaml
spring:
  datasource:
    hikari:
      # 연결 풀 최적화
      maximum-pool-size: 20
      minimum-idle: 10
      connection-timeout: 20000
      idle-timeout: 300000
      max-lifetime: 1200000
      validation-timeout: 5000
      leak-detection-threshold: 60000
      
  jpa:
    properties:
      hibernate:
        # 배치 처리 최적화
        jdbc:
          batch_size: 50
          order_inserts: true
          order_updates: true
          batch_versioned_data: true
        # 캐시 설정
        cache:
          use_second_level_cache: true
          use_query_cache: true
          region:
            factory_class: org.hibernate.cache.jcache.JCacheRegionFactory
        # 통계 수집
        generate_statistics: true
        session:
          events:
            log:
              LOG_QUERIES_SLOWER_THAN_MS: 1000
```

### 7.3 애플리케이션 성능 설정

```yaml
gpu:
  management:
    # 메트릭 수집 최적화
    metrics:
      collection-interval: 30s
      batch-size: 100
      parallel-collection: true
      thread-pool-size: 5
      
    # 캐시 최적화
    cache:
      enabled: true
      implementation: caffeine
      spec: |
        maximumSize=1000,
        expireAfterWrite=5m,
        recordStats
        
    # 배치 처리 최적화
    batch:
      enabled: true
      pool-size: 10
      queue-capacity: 1000
      keep-alive-seconds: 60
```

## 8. 백업 및 복구 설정

### 8.1 데이터베이스 백업 설정

#### backup-cronjob.yaml
```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: gpu-db-backup
  namespace: gpu-management
spec:
  schedule: "0 2 * * *"
  jobTemplate:
    spec:
      template:
        spec:
          restartPolicy: OnFailure
          containers:
          - name: backup
            image: oracle/instantclient:19.3.0
            command:
            - /bin/bash
            - -c
            - |
              expdp gpu_admin/password@oracle-db:1521/ORCL \
              DIRECTORY=backup_dir \
              DUMPFILE=gpu_backup_$(date +%Y%m%d_%H%M%S).dmp \
              LOGFILE=gpu_backup_$(date +%Y%m%d_%H%M%S).log \
              SCHEMAS=gpu_admin
            volumeMounts:
            - name: backup-storage
              mountPath: /backup
          volumes:
          - name: backup-storage
            persistentVolumeClaim:
              claimName: backup-pvc
```

### 8.2 설정 백업

#### config-backup.sh
```bash
#!/bin/bash

BACKUP_DIR="/backup/config/$(date +%Y%m%d_%H%M%S)"
mkdir -p "$BACKUP_DIR"

# ConfigMap 백업
kubectl get configmap gpu-management-config -o yaml > "$BACKUP_DIR/configmap.yaml"

# Secret 백업 (민감한 정보 제외)
kubectl get secret gpu-management-secrets -o yaml | \
  sed 's/^  [^:]*: .*/  &: <REDACTED>/' > "$BACKUP_DIR/secrets.yaml"

# 배포 설정 백업
kubectl get deployment k8s-gpu-management -o yaml > "$BACKUP_DIR/deployment.yaml"
kubectl get service k8s-gpu-management -o yaml > "$BACKUP_DIR/service.yaml"

echo "Backup completed: $BACKUP_DIR"
```

## 9. 환경 변수 참조

### 9.1 시스템 환경 변수

| 변수명 | 기본값 | 설명 |
|--------|--------|------|
| `SPRING_PROFILES_ACTIVE` | development | 활성 프로파일 |
| `DB_HOST` | localhost | 데이터베이스 호스트 |
| `DB_PORT` | 1521 | 데이터베이스 포트 |
| `DB_SERVICE` | ORCL | Oracle 서비스명 |
| `DB_USERNAME` | gpu_admin | DB 사용자명 |
| `DB_PASSWORD` | - | DB 패스워드 (필수) |
| `CLUSTER_NAME` | default | 클러스터 이름 |

### 9.2 GPU 관리 환경 변수

| 변수명 | 기본값 | 설명 |
|--------|--------|------|
| `GPU_METRICS_INTERVAL` | 30s | 메트릭 수집 간격 |
| `GPU_METRICS_RETENTION` | 30 | 메트릭 보존 일수 |
| `GPU_NVIDIA_SMI_PATH` | /usr/bin/nvidia-smi | nvidia-smi 경로 |
| `GPU_ALLOCATION_DEFAULT_HOURS` | 24 | 기본 할당 시간 |
| `GPU_COST_CURRENCY` | USD | 비용 통화 |
| `GPU_ALERT_TEMP` | 85.0 | 온도 알람 임계값 |

### 9.3 알림 환경 변수

| 변수명 | 기본값 | 설명 |
|--------|--------|------|
| `SLACK_WEBHOOK_URL` | - | Slack 웹훅 URL |
| `SMTP_HOST` | localhost | SMTP 서버 |
| `SMTP_PORT` | 587 | SMTP 포트 |
| `SMTP_USERNAME` | - | SMTP 사용자명 |
| `SMTP_PASSWORD` | - | SMTP 패스워드 |

## 10. 문제 해결

### 10.1 설정 검증

#### 설정 유효성 확인
```bash
# 설정 파일 구문 검사
kubectl apply --dry-run=client -f k8s/

# 환경 변수 확인
kubectl exec deployment/k8s-gpu-management -- env | grep GPU

# 설정 값 조회
curl -s http://localhost:8080/k8s-monitor/actuator/configprops | jq '.gpu'
```

### 10.2 일반적인 설정 문제

#### 데이터베이스 연결 실패
```yaml
# 연결 테스트
spring:
  datasource:
    url: jdbc:oracle:thin:@oracle-host:1521:ORCL
    # 연결 테스트 쿼리 추가
    test-while-idle: true
    validation-query: SELECT 1 FROM DUAL
```

#### 메트릭 수집 실패
```yaml
gpu:
  management:
    metrics:
      nvidia-smi:
        enabled: false  # 개발 환경에서는 비활성화
      # 또는 경로 수정
      nvidia-smi:
        path: /usr/local/bin/nvidia-smi
```

### 10.3 로그 레벨 동적 변경

```bash
# 로그 레벨 확인
curl -s http://localhost:8080/k8s-monitor/actuator/loggers/com.k8s.monitor

# 로그 레벨 변경
curl -X POST http://localhost:8080/k8s-monitor/actuator/loggers/com.k8s.monitor \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'
```

## 11. 마이그레이션 가이드

### 11.1 버전 업그레이드

#### v1.0 to v1.1 설정 변경사항
```yaml
# 새로운 설정 추가
gpu:
  management:
    # v1.1에서 추가된 기능
    forecasting:
      enabled: true
      algorithm: linear-regression
    
    # 변경된 설정 (이전 버전과 호환)
    alerts:
      # 기존: temperature-threshold
      # 신규: thresholds.temperature
      thresholds:
        temperature: 85.0
        utilization: 90.0
        memory: 95.0
```

### 11.2 데이터베이스 마이그레이션

#### Flyway 설정
```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    baseline-version: 1
```

## 12. 모범 사례

### 12.1 설정 관리 모범 사례

1. **환경별 설정 분리**
   - 개발/스테이징/운영 환경별 프로파일 사용
   - 민감한 정보는 Secret으로 관리

2. **설정 검증**
   - ConfigMap 변경 시 롤링 업데이트 수행
   - 설정 변경 전 백업 생성

3. **모니터링 설정**
   - 모든 환경에서 메트릭 수집 활성화
   - 알람 임계값은 환경에 맞게 조정

### 12.2 보안 모범 사례

1. **최소 권한 원칙**
   - 필요한 최소한의 RBAC 권한만 부여
   - 네트워크 정책으로 트래픽 제한

2. **인증서 관리**
   - TLS 인증서 자동 갱신 설정
   - 내부 통신도 TLS 사용

3. **Secret 관리**
   - External Secrets Operator 사용 권장
   - 정기적인 패스워드 로테이션

이 설정 가이드를 통해 GPU 관리 시스템을 안전하고 효율적으로 운영할 수 있습니다.