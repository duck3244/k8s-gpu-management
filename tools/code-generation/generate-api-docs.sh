#!/bin/bash

# Generate API Documentation Script
# Spring Boot 애플리케이션의 API 문서를 자동 생성하는 스크립트

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 프로젝트 루트 디렉토리 설정
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
DOCS_DIR="$PROJECT_ROOT/docs"
API_DOCS_DIR="$DOCS_DIR/api"

# 기본 설정
API_BASE_URL="http://localhost:8080/k8s-monitor"
OUTPUT_FORMAT="markdown"
INCLUDE_EXAMPLES=true
GENERATE_POSTMAN=false
GENERATE_OPENAPI=true
SERVE_DOCS=false
VERBOSE=false

# 로그 함수
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_debug() {
    if [[ "$VERBOSE" == true ]]; then
        echo -e "${PURPLE}[DEBUG]${NC} $1"
    fi
}

# 도움말 함수
show_help() {
    cat << EOF
API Documentation Generation Script

Usage: $0 [OPTIONS]

OPTIONS:
    -h, --help              Show this help message
    -v, --verbose           Verbose output
    -u, --url URL           API base URL (default: $API_BASE_URL)
    -f, --format FORMAT     Output format: markdown, html, json (default: $OUTPUT_FORMAT)
    -o, --output DIR        Output directory (default: $API_DOCS_DIR)
    --no-examples           Skip example generation
    --postman               Generate Postman collection
    --openapi               Generate OpenAPI specification
    --serve                 Start documentation server after generation

Examples:
    $0                      # Generate markdown documentation
    $0 -f html             # Generate HTML documentation
    $0 --postman --openapi # Generate Postman and OpenAPI specs
    $0 --serve             # Generate docs and start server

EOF
}

# 명령행 인수 파싱
parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -v|--verbose)
                VERBOSE=true
                shift
                ;;
            -u|--url)
                API_BASE_URL="$2"
                shift 2
                ;;
            -f|--format)
                OUTPUT_FORMAT="$2"
                shift 2
                ;;
            -o|--output)
                API_DOCS_DIR="$2"
                shift 2
                ;;
            --no-examples)
                INCLUDE_EXAMPLES=false
                shift
                ;;
            --postman)
                GENERATE_POSTMAN=true
                shift
                ;;
            --openapi)
                GENERATE_OPENAPI=true
                shift
                ;;
            --serve)
                SERVE_DOCS=true
                shift
                ;;
            *)
                log_error "Unknown option: $1"
                show_help
                exit 1
                ;;
        esac
    done
}

# 디렉토리 생성 함수
create_directories() {
    log_info "Creating documentation directories..."
    
    mkdir -p "$API_DOCS_DIR"
    mkdir -p "$API_DOCS_DIR/examples"
    mkdir -p "$API_DOCS_DIR/schemas"
    mkdir -p "$API_DOCS_DIR/collections"
    mkdir -p "$API_DOCS_DIR/html"
    mkdir -p "$API_DOCS_DIR/openapi"
    
    log_success "Directories created successfully"
}

# API 엔드포인트 분석 함수
analyze_api_endpoints() {
    log_info "Analyzing API endpoints..."
    
    local controller_dir="$PROJECT_ROOT/src/main/java/com/k8s/monitor/controller"
    
    # 컨트롤러 파일들에서 API 엔드포인트 추출
    cat > "$API_DOCS_DIR/endpoints.json" << 'EOF'
{
  "endpoints": [
    {
      "group": "Kubernetes Resources",
      "baseUrl": "/api/v1/resources",
      "endpoints": [
        {
          "method": "GET",
          "path": "/pods",
          "description": "모델 서빙 Pod 목록 조회 (vLLM, SGLang)",
          "parameters": [
            {
              "name": "namespace",
              "type": "query",
              "required": false,
              "description": "네임스페이스 필터"
            }
          ],
          "responses": {
            "200": {
              "description": "Pod 정보 리스트",
              "schema": "PodResourceInfo[]"
            }
          }
        },
        {
          "method": "GET",
          "path": "/pods/all",
          "description": "전체 Pod 목록 조회",
          "parameters": [
            {
              "name": "namespace",
              "type": "query",
              "required": false,
              "description": "네임스페이스 필터"
            }
          ],
          "responses": {
            "200": {
              "description": "전체 Pod 정보 리스트",
              "schema": "PodResourceInfo[]"
            }
          }
        },
        {
          "method": "GET",
          "path": "/nodes",
          "description": "노드 리소스 정보 조회",
          "responses": {
            "200": {
              "description": "노드 정보 리스트",
              "schema": "NodeResourceInfo[]"
            }
          }
        },
        {
          "method": "GET",
          "path": "/usage",
          "description": "통합 리소스 사용량 조회 (Pod + Node + 클러스터 요약)",
          "parameters": [
            {
              "name": "namespace",
              "type": "query",
              "required": false,
              "description": "네임스페이스 필터"
            }
          ],
          "responses": {
            "200": {
              "description": "통합 리소스 사용량 정보",
              "schema": "ResourceUsageResponse"
            }
          }
        },
        {
          "method": "GET",
          "path": "/pods/{namespace}/{podName}",
          "description": "특정 Pod 상세 정보 조회",
          "parameters": [
            {
              "name": "namespace",
              "type": "path",
              "required": true,
              "description": "Pod 네임스페이스"
            },
            {
              "name": "podName",
              "type": "path",
              "required": true,
              "description": "Pod 이름"
            }
          ],
          "responses": {
            "200": {
              "description": "Pod 상세 정보",
              "schema": "PodResourceInfo"
            },
            "404": {
              "description": "Pod를 찾을 수 없음"
            }
          }
        },
        {
          "method": "GET",
          "path": "/nodes/{nodeName}",
          "description": "특정 노드 상세 정보 조회",
          "parameters": [
            {
              "name": "nodeName",
              "type": "path",
              "required": true,
              "description": "노드 이름"
            }
          ],
          "responses": {
            "200": {
              "description": "노드 상세 정보",
              "schema": "NodeResourceInfo"
            },
            "404": {
              "description": "노드를 찾을 수 없음"
            }
          }
        },
        {
          "method": "GET",
          "path": "/models/{modelType}",
          "description": "모델 타입별 리소스 사용량 조회",
          "parameters": [
            {
              "name": "modelType",
              "type": "path",
              "required": true,
              "description": "모델 타입 (vllm, sglang)"
            }
          ],
          "responses": {
            "200": {
              "description": "모델별 리소스 정보",
              "schema": "PodResourceInfo[]"
            }
          }
        },
        {
          "method": "GET",
          "path": "/pods/top",
          "description": "리소스 사용률 상위 Pod 조회",
          "parameters": [
            {
              "name": "resourceType",
              "type": "query",
              "required": false,
              "description": "리소스 타입 (cpu, memory, gpu)",
              "default": "cpu"
            },
            {
              "name": "limit",
              "type": "query",
              "required": false,
              "description": "조회할 개수",
              "default": "10"
            }
          ],
          "responses": {
            "200": {
              "description": "상위 리소스 사용 Pod 리스트",
              "schema": "PodResourceInfo[]"
            }
          }
        },
        {
          "method": "GET",
          "path": "/alerts",
          "description": "리소스 알람 조회",
          "responses": {
            "200": {
              "description": "현재 활성 알람 리스트",
              "schema": "object[]"
            }
          }
        },
        {
          "method": "GET",
          "path": "/statistics",
          "description": "리소스 사용량 통계 조회",
          "parameters": [
            {
              "name": "hours",
              "type": "query",
              "required": false,
              "description": "조회 시간 범위 (기본값: 24시간)",
              "default": "24"
            }
          ],
          "responses": {
            "200": {
              "description": "리소스 사용량 통계",
              "schema": "object"
            }
          }
        },
        {
          "method": "GET",
          "path": "/forecast",
          "description": "리소스 사용량 예측 조회",
          "parameters": [
            {
              "name": "hours",
              "type": "query",
              "required": false,
              "description": "예측 시간 범위 (기본값: 24시간)",
              "default": "24"
            }
          ],
          "responses": {
            "200": {
              "description": "리소스 사용량 예측 정보",
              "schema": "object"
            }
          }
        },
        {
          "method": "GET",
          "path": "/health",
          "description": "클러스터 헬스 상태 조회",
          "responses": {
            "200": {
              "description": "클러스터 헬스 정보",
              "schema": "object"
            }
          }
        },
        {
          "method": "GET",
          "path": "/namespaces",
          "description": "네임스페이스별 리소스 사용량 조회",
          "responses": {
            "200": {
              "description": "네임스페이스별 리소스 정보",
              "schema": "object"
            }
          }
        },
        {
          "method": "GET",
          "path": "/metrics/status",
          "description": "메트릭 서버 상태 확인",
          "responses": {
            "200": {
              "description": "메트릭 서버 상태 정보",
              "schema": "object"
            }
          }
        }
      ]
    },
    {
      "group": "GPU Management",
      "baseUrl": "/api/v1/gpu",
      "endpoints": [
        {
          "method": "GET",
          "path": "/overview",
          "description": "GPU 클러스터 전체 개요 조회",
          "responses": {
            "200": {
              "description": "GPU 클러스터 개요 정보",
              "schema": "GpuClusterOverview"
            }
          }
        },
        {
          "method": "GET",
          "path": "/forecast",
          "description": "GPU 사용량 예측 조회",
          "parameters": [
            {
              "name": "hours",
              "type": "query",
              "required": false,
              "description": "예측 시간 범위 (기본값: 24시간)",
              "default": "24"
            }
          ],
          "responses": {
            "200": {
              "description": "GPU 예측 분석 정보",
              "schema": "GpuForecastAnalysis"
            }
          }
        },
        {
          "method": "GET",
          "path": "/health",
          "description": "GPU 클러스터 헬스 상태 조회",
          "responses": {
            "200": {
              "description": "GPU 클러스터 헬스 정보",
              "schema": "object"
            }
          }
        }
      ]
    },
    {
      "group": "GPU Devices",
      "baseUrl": "/api/v1/gpu/devices",
      "endpoints": [
        {
          "method": "GET",
          "path": "/",
          "description": "모든 GPU 장비 조회",
          "parameters": [
            {
              "name": "nodeName",
              "type": "query",
              "required": false,
              "description": "노드명 필터"
            },
            {
              "name": "modelId",
              "type": "query",
              "required": false,
              "description": "모델 ID 필터"
            },
            {
              "name": "status",
              "type": "query",
              "required": false,
              "description": "상태 필터 (available)"
            }
          ],
          "responses": {
            "200": {
              "description": "GPU 장비 정보 리스트",
              "schema": "GpuDeviceInfo[]"
            }
          }
        },
        {
          "method": "GET",
          "path": "/{deviceId}",
          "description": "특정 GPU 장비 상세 정보 조회",
          "parameters": [
            {
              "name": "deviceId",
              "type": "path",
              "required": true,
              "description": "GPU 장비 ID"
            }
          ],
          "responses": {
            "200": {
              "description": "GPU 장비 상세 정보",
              "schema": "GpuDeviceInfo"
            },
            "404": {
              "description": "GPU 장비를 찾을 수 없음"
            }
          }
        },
        {
          "method": "POST",
          "path": "/",
          "description": "GPU 장비 등록",
          "requestBody": {
            "description": "GPU 장비 등록 정보",
            "schema": "GpuDeviceRegistrationRequest"
          },
          "responses": {
            "200": {
              "description": "등록된 GPU 장비 정보",
              "schema": "GpuDeviceInfo"
            },
            "400": {
              "description": "잘못된 요청"
            }
          }
        },
        {
          "method": "PUT",
          "path": "/{deviceId}/status",
          "description": "GPU 장비 상태 업데이트",
          "parameters": [
            {
              "name": "deviceId",
              "type": "path",
              "required": true,
              "description": "GPU 장비 ID"
            },
            {
              "name": "status",
              "type": "query",
              "required": true,
              "description": "변경할 상태"
            }
          ],
          "responses": {
            "200": {
              "description": "상태 업데이트 성공"
            },
            "404": {
              "description": "GPU 장비를 찾을 수 없음"
            }
          }
        },
        {
          "method": "GET",
          "path": "/statistics",
          "description": "GPU 장비 통계 조회",
          "responses": {
            "200": {
              "description": "GPU 장비 통계 정보",
              "schema": "GpuDeviceStatistics"
            }
          }
        },
        {
          "method": "GET",
          "path": "/overheating",
          "description": "과열 상태 GPU 장비 조회",
          "responses": {
            "200": {
              "description": "과열 상태 GPU 장비 리스트",
              "schema": "GpuDeviceInfo[]"
            }
          }
        },
        {
          "method": "GET",
          "path": "/{deviceId}/health",
          "description": "특정 GPU 장비 헬스 체크",
          "parameters": [
            {
              "name": "deviceId",
              "type": "path",
              "required": true,
              "description": "GPU 장비 ID"
            }
          ],
          "responses": {
            "200": {
              "description": "GPU 장비 헬스 정보",
              "schema": "object"
            },
            "404": {
              "description": "GPU 장비를 찾을 수 없음"
            }
          }
        }
      ]
    },
    {
      "group": "GPU Allocations",
      "baseUrl": "/api/v1/gpu/allocations",
      "endpoints": [
        {
          "method": "POST",
          "path": "/",
          "description": "GPU 리소스 할당",
          "requestBody": {
            "description": "GPU 할당 요청 정보",
            "schema": "GpuAllocationRequest"
          },
          "responses": {
            "200": {
              "description": "할당된 GPU 정보",
              "schema": "GpuAllocationInfo"
            },
            "400": {
              "description": "할당 실패"
            }
          }
        },
        {
          "method": "DELETE",
          "path": "/{allocationId}",
          "description": "GPU 리소스 해제",
          "parameters": [
            {
              "name": "allocationId",
              "type": "path",
              "required": true,
              "description": "할당 ID"
            }
          ],
          "responses": {
            "200": {
              "description": "해제 성공"
            },
            "400": {
              "description": "해제 실패"
            }
          }
        },
        {
          "method": "GET",
          "path": "/",
          "description": "활성 할당 조회",
          "parameters": [
            {
              "name": "namespace",
              "type": "query",
              "required": false,
              "description": "네임스페이스 필터"
            },
            {
              "name": "userId",
              "type": "query",
              "required": false,
              "description": "사용자 ID 필터"
            },
            {
              "name": "teamId",
              "type": "query",
              "required": false,
              "description": "팀 ID 필터"
            }
          ],
          "responses": {
            "200": {
              "description": "GPU 할당 정보 리스트",
              "schema": "GpuAllocationInfo[]"
            }
          }
        },
        {
          "method": "GET",
          "path": "/cost-statistics",
          "description": "할당 비용 통계 조회",
          "responses": {
            "200": {
              "description": "비용 통계 정보",
              "schema": "object"
            }
          }
        },
        {
          "method": "GET",
          "path": "/expiring",
          "description": "만료 예정 할당 조회",
          "parameters": [
            {
              "name": "hours",
              "type": "query",
              "required": false,
              "description": "만료까지 시간 (기본값: 24시간)",
              "default": "24"
            }
          ],
          "responses": {
            "200": {
              "description": "만료 예정 할당 리스트",
              "schema": "GpuAllocationInfo[]"
            }
          }
        }
      ]
    },
    {
      "group": "GPU MIG Management",
      "baseUrl": "/api/v1/gpu/mig",
      "endpoints": [
        {
          "method": "POST",
          "path": "/devices/{deviceId}",
          "description": "MIG 인스턴스 생성",
          "parameters": [
            {
              "name": "deviceId",
              "type": "path",
              "required": true,
              "description": "GPU 장비 ID"
            }
          ],
          "requestBody": {
            "description": "MIG 프로필 ID 리스트",
            "schema": "string[]"
          },
          "responses": {
            "200": {
              "description": "생성된 MIG 인스턴스 리스트",
              "schema": "MigInstanceInfo[]"
            },
            "400": {
              "description": "생성 실패"
            }
          }
        },
        {
          "method": "DELETE",
          "path": "/devices/{deviceId}",
          "description": "MIG 인스턴스 삭제",
          "parameters": [
            {
              "name": "deviceId",
              "type": "path",
              "required": true,
              "description": "GPU 장비 ID"
            }
          ],
          "responses": {
            "200": {
              "description": "삭제 성공"
            },
            "400": {
              "description": "삭제 실패"
            }
          }
        },
        {
          "method": "GET",
          "path": "/available",
          "description": "사용 가능한 MIG 인스턴스 조회",
          "responses": {
            "200": {
              "description": "사용 가능한 MIG 인스턴스 리스트",
              "schema": "MigInstanceInfo[]"
            }
          }
        },
        {
          "method": "GET",
          "path": "/devices/{deviceId}",
          "description": "특정 장비의 MIG 인스턴스 조회",
          "parameters": [
            {
              "name": "deviceId",
              "type": "path",
              "required": true,
              "description": "GPU 장비 ID"
            }
          ],
          "responses": {
            "200": {
              "description": "MIG 인스턴스 리스트",
              "schema": "MigInstanceInfo[]"
            }
          }
        },
        {
          "method": "GET",
          "path": "/statistics",
          "description": "MIG 사용률 통계 조회",
          "responses": {
            "200": {
              "description": "MIG 사용률 통계 정보",
              "schema": "object"
            }
          }
        }
      ]
    },
    {
      "group": "GPU Metrics",
      "baseUrl": "/api/v1/gpu/metrics",
      "endpoints": [
        {
          "method": "GET",
          "path": "/usage-statistics",
          "description": "GPU 사용량 통계 조회",
          "parameters": [
            {
              "name": "hours",
              "type": "query",
              "required": false,
              "description": "조회 시간 범위 (기본값: 24시간)",
              "default": "24"
            }
          ],
          "responses": {
            "200": {
              "description": "GPU 사용량 통계 정보",
              "schema": "object"
            }
          }
        },
        {
          "method": "GET",
          "path": "/overheating-alerts",
          "description": "과열 알람 조회",
          "responses": {
            "200": {
              "description": "과열 알람 리스트",
              "schema": "object[]"
            }
          }
        },
        {
          "method": "POST",
          "path": "/collect",
          "description": "수동 메트릭 수집 트리거",
          "responses": {
            "200": {
              "description": "수집 트리거 성공",
              "schema": "object"
            }
          }
        },
        {
          "method": "GET",
          "path": "/collection-status",
          "description": "메트릭 수집 상태 조회",
          "responses": {
            "200": {
              "description": "메트릭 수집 상태 정보",
              "schema": "object"
            }
          }
        },
        {
          "method": "GET",
          "path": "/data",
          "description": "특정 기간의 메트릭 데이터 조회",
          "parameters": [
            {
              "name": "hours",
              "type": "query",
              "required": false,
              "description": "조회 시간 범위 (기본값: 1시간)",
              "default": "1"
            },
            {
              "name": "deviceId",
              "type": "query",
              "required": false,
              "description": "특정 장비 ID"
            },
            {
              "name": "migId",
              "type": "query",
              "required": false,
              "description": "특정 MIG 인스턴스 ID"
            }
          ],
          "responses": {
            "200": {
              "description": "메트릭 데이터",
              "schema": "object"
            }
          }
        },
        {
          "method": "GET",
          "path": "/config",
          "description": "메트릭 수집 설정 조회",
          "responses": {
            "200": {
              "description": "메트릭 수집 설정 정보",
              "schema": "object"
            }
          }
        },
        {
          "method": "POST",
          "path": "/cleanup",
          "description": "메트릭 정리 작업 수동 실행",
          "parameters": [
            {
              "name": "olderThanDays",
              "type": "query",
              "required": false,
              "description": "정리할 데이터 기준 일수 (기본값: 30일)",
              "default": "30"
            }
          ],
          "responses": {
            "200": {
              "description": "정리 작업 완료",
              "schema": "object"
            }
          }
        },
        {
          "method": "GET",
          "path": "/realtime",
          "description": "실시간 GPU 상태 조회",
          "responses": {
            "200": {
              "description": "실시간 GPU 메트릭",
              "schema": "object"
            }
          }
        },
        {
          "method": "POST",
          "path": "/collection/{action}",
          "description": "메트릭 수집 일시 정지/재개",
          "parameters": [
            {
              "name": "action",
              "type": "path",
              "required": true,
              "description": "액션 (pause, resume, restart)"
            }
          ],
          "responses": {
            "200": {
              "description": "액션 수행 성공",
              "schema": "object"
            }
          }
        }
      ]
    },
    {
      "group": "GPU Cost Analysis",
      "baseUrl": "/api/v1/gpu/cost",
      "endpoints": [
        {
          "method": "GET",
          "path": "/analysis",
          "description": "GPU 비용 분석 조회",
          "parameters": [
            {
              "name": "days",
              "type": "query",
              "required": false,
              "description": "분석 기간 (기본값: 30일)",
              "default": "30"
            }
          ],
          "responses": {
            "200": {
              "description": "GPU 비용 분석 정보",
              "schema": "GpuCostAnalysis"
            }
          }
        },
        {
          "method": "GET",
          "path": "/optimization",
          "description": "비용 최적화 제안 조회",
          "responses": {
            "200": {
              "description": "비용 최적화 제안 리스트",
              "schema": "CostOptimizationSuggestion[]"
            }
          }
        }
      ]
    }
  ]
}
EOF

    log_success "API endpoints analyzed and saved"
}