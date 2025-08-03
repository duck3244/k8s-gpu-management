#!/bin/bash

# GPU Health Check Script
# GPU 장비의 상태를 종합적으로 점검하는 스크립트

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

# 설정 변수
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
LOG_FILE="/var/log/gpu-health-check.log"
TEMP_DIR="/tmp/gpu-health-check"
API_BASE_URL="http://localhost:8080/k8s-monitor"

# 임계값 설정
TEMP_WARNING_THRESHOLD=80
TEMP_CRITICAL_THRESHOLD=85
UTILIZATION_HIGH_THRESHOLD=90
MEMORY_HIGH_THRESHOLD=95
POWER_HIGH_THRESHOLD=400

# 체크 결과 저장
declare -A check_results
declare -A device_status
declare -A alerts

# 로그 함수들
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1" | tee -a "$LOG_FILE"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1" | tee -a "$LOG_FILE"
}

log_warn() {
    echo -e "${YELLOW}[WARNING]${NC} $1" | tee -a "$LOG_FILE"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1" | tee -a "$LOG_FILE"
}

log_critical() {
    echo -e "${RED}[CRITICAL]${NC} $1" | tee -a "$LOG_FILE"
}

# 도움말 함수
show_help() {
    cat << EOF
GPU Health Check Script

Usage: $0 [OPTIONS]

OPTIONS:
    -h, --help              Show this help message
    -v, --verbose           Verbose output
    -q, --quiet             Quiet mode (errors only)
    -o, --output FILE       Output file for results
    -f, --format FORMAT     Output format: text, json, html (default: text)
    --api-url URL          API base URL (default: $API_BASE_URL)
    --temp-warning TEMP     Temperature warning threshold (default: $TEMP_WARNING_THRESHOLD°C)
    --temp-critical TEMP    Temperature critical threshold (default: $TEMP_CRITICAL_THRESHOLD°C)
    --mail-to EMAIL         Send results via email
    --slack-webhook URL     Send alerts to Slack
    --no-nvidia-smi         Skip nvidia-smi checks
    --no-api                Skip API checks
    --fix-issues            Attempt to fix detected issues
    --daemon                Run as daemon (continuous monitoring)
    --interval SECONDS      Check interval for daemon mode (default: 300)

Examples:
    $0                      # Basic health check
    $0 -v                   # Verbose health check
    $0 --format json        # JSON output
    $0 --daemon --interval 60  # Run as daemon, check every minute
    $0 --fix-issues         # Check and attempt to fix issues
    $0 --mail-to admin@company.com  # Send results via email

EOF
}

# 초기화 함수
initialize() {
    log_info "Initializing GPU health check..."
    
    # 임시 디렉토리 생성
    mkdir -p "$TEMP_DIR"
    
    # 로그 파일 초기화
    if [[ ! -f "$LOG_FILE" ]]; then
        touch "$LOG_FILE"
        chmod 644 "$LOG_FILE"
    fi
    
    # 결과 초기화
    check_results["nvidia_driver"]=0
    check_results["nvidia_smi"]=0
    check_results["api_connection"]=0
    check_results["gpu_devices"]=0
    check_results["temperature"]=0
    check_results["memory"]=0
    check_results["utilization"]=0
    check_results["power"]=0
    
    log_success "Initialization completed"
}

# NVIDIA 드라이버 상태 확인
check_nvidia_driver() {
    log_info "Checking NVIDIA driver status..."
    
    if lsmod | grep -q nvidia; then
        local driver_version=$(nvidia-smi --query-gpu=driver_version --format=csv,noheader,nounits | head -1)
        log_success "NVIDIA driver loaded: $driver_version"
        check_results["nvidia_driver"]=1
        
        # 드라이버 버전 검증
        if [[ -n "$driver_version" ]] && [[ "$driver_version" != "N/A" ]]; then
            local major_version=$(echo "$driver_version" | cut -d. -f1)
            if [[ "$major_version" -ge 470 ]]; then
                log_success "Driver version is up to date"
            else
                log_warn "Driver version might be outdated: $driver_version"
            fi
        fi
    else
        log_error "NVIDIA driver not loaded"
        check_results["nvidia_driver"]=0
        alerts["driver"]="NVIDIA driver not loaded"
    fi
}

# nvidia-smi 상태 확인
check_nvidia_smi() {
    log_info "Checking nvidia-smi functionality..."
    
    if command -v nvidia-smi >/dev/null 2>&1; then
        if nvidia-smi > /dev/null 2>&1; then
            log_success "nvidia-smi working correctly"
            check_results["nvidia_smi"]=1
            
            # GPU 개수 확인
            local gpu_count=$(nvidia-smi --list-gpus | wc -l)
            log_info "Detected $gpu_count GPU(s)"
            
            return 0
        else
            log_error "nvidia-smi command failed"
            check_results["nvidia_smi"]=0
            alerts["nvidia_smi"]="nvidia-smi command execution failed"
            return 1
        fi
    else
        log_error "nvidia-smi not found"
        check_results["nvidia_smi"]=0
        alerts["nvidia_smi"]="nvidia-smi command not found"
        return 1
    fi
}

# API 연결 상태 확인
check_api_connection() {
    log_info "Checking API connection..."
    
    if command -v curl >/dev/null 2>&1; then
        local api_url="$API_BASE_URL/actuator/health"
        
        if curl -s --connect-timeout 5 "$api_url" > /dev/null 2>&1; then
            log_success "API connection successful"
            check_results["api_connection"]=1
            
            # API 버전 확인
            local version_url="$API_BASE_URL/actuator/info"
            local version_info=$(curl -s "$version_url" 2>/dev/null)
            if [[ -n "$version_info" ]]; then
                log_info "API version info available"
            fi
            
        else
            log_warn "API connection failed or service not responding"
            check_results["api_connection"]=0
            alerts["api"]="API service not responding at $API_BASE_URL"
        fi
    else
        log_warn "curl not available, skipping API check"
        check_results["api_connection"]=-1
    fi
}

# GPU 장비 상태 확인
check_gpu_devices() {
    log_info "Checking individual GPU devices..."
    
    if ! check_nvidia_smi; then
        log_error "Cannot check GPU devices without nvidia-smi"
        return 1
    fi
    
    local gpu_count=$(nvidia-smi --list-gpus | wc -l)
    local healthy_gpus=0
    local warning_gpus=0
    local critical_gpus=0
    
    for ((i=0; i<gpu_count; i++)); do
        log_info "Checking GPU $i..."
        
        # GPU 정보 수집
        local gpu_info=$(nvidia-smi -i $i --query-gpu=name,temperature.gpu,utilization.gpu,memory.used,memory.total,power.draw,power.limit --format=csv,noheader,nounits)
        
        if [[ -n "$gpu_info" ]]; then
            IFS=',' read -r name temp util mem_used mem_total power_draw power_limit <<< "$gpu_info"
            
            # 공백 제거
            name=$(echo "$name" | xargs)
            temp=$(echo "$temp" | xargs)
            util=$(echo "$util" | xargs)
            mem_used=$(echo "$mem_used" | xargs)
            mem_total=$(echo "$mem_total" | xargs)
            power_draw=$(echo "$power_draw" | xargs)
            power_limit=$(echo "$power_limit" | xargs)
            
            log_info "GPU $i ($name):"
            log_info "  Temperature: ${temp}°C"
            log_info "  Utilization: ${util}%"
            log_info "  Memory: ${mem_used}MB/${mem_total}MB"
            log_info "  Power: ${power_draw}W/${power_limit}W"
            
            # 상태 평가
            local gpu_status="HEALTHY"
            local issues=()
            
            # 온도 체크
            if [[ "$temp" != "N/A" ]] && [[ "$temp" =~ ^[0-9]+$ ]]; then
                if [[ $temp -ge $TEMP_CRITICAL_THRESHOLD ]]; then
                    gpu_status="CRITICAL"
                    issues+=("Temperature critical: ${temp}°C")
                    critical_gpus=$((critical_gpus + 1))
                elif [[ $temp -ge $TEMP_WARNING_THRESHOLD ]]; then
                    if [[ "$gpu_status" != "CRITICAL" ]]; then
                        gpu_status="WARNING"
                    fi
                    issues+=("Temperature high: ${temp}°C")
                    warning_gpus=$((warning_gpus + 1))
                fi
            fi
            
            # 메모리 사용률 체크
            if [[ "$mem_used" != "N/A" ]] && [[ "$mem_total" != "N/A" ]] && [[ "$mem_total" -gt 0 ]]; then
                local mem_percent=$((mem_used * 100 / mem_total))
                if [[ $mem_percent -ge $MEMORY_HIGH_THRESHOLD ]]; then
                    if [[ "$gpu_status" != "CRITICAL" ]]; then
                        gpu_status="WARNING"
                    fi
                    issues+=("Memory usage high: ${mem_percent}%")
                    warning_gpus=$((warning_gpus + 1))
                fi
            fi
            
            # 전력 소모 체크
            if [[ "$power_draw" != "N/A" ]] && [[ "$power_limit" != "N/A" ]] && [[ "$power_limit" -gt 0 ]]; then
                local power_percent=$((power_draw * 100 / power_limit))
                if [[ $power_percent -ge 90 ]]; then
                    if [[ "$gpu_status" != "CRITICAL" ]]; then
                        gpu_status="WARNING"
                    fi
                    issues+=("Power consumption high: ${power_percent}%")
                fi
            fi
            
            # 상태 저장
            device_status["gpu_$i"]="$gpu_status"
            
            # 결과 출력
            case "$gpu_status" in
                "HEALTHY")
                    log_success "GPU $i status: HEALTHY"
                    healthy_gpus=$((healthy_gpus + 1))
                    ;;
                "WARNING")
                    log_warn "GPU $i status: WARNING - ${issues[*]}"
                    alerts["gpu_$i"]="${issues[*]}"
                    ;;
                "CRITICAL")
                    log_critical "GPU $i status: CRITICAL - ${issues[*]}"
                    alerts["gpu_$i"]="${issues[*]}"
                    ;;
            esac
        else
            log_error "Failed to get information for GPU $i"
            device_status["gpu_$i"]="ERROR"
            alerts["gpu_$i"]="Failed to retrieve GPU information"
        fi
    done
    
    # 전체 GPU 상태 요약
    log_info "GPU Status Summary:"
    log_info "  Healthy: $healthy_gpus"
    log_info "  Warning: $warning_gpus"
    log_info "  Critical: $critical_gpus"
    
    if [[ $critical_gpus -gt 0 ]]; then
        check_results["gpu_devices"]=0
        log_critical "$critical_gpus GPU(s) in critical state"
    elif [[ $warning_gpus -gt 0 ]]; then
        check_results["gpu_devices"]=1
        log_warn "$warning_gpus GPU(s) in warning state"
    else
        check_results["gpu_devices"]=2
        log_success "All GPUs healthy"
    fi
}

# 온도 상세 분석
check_temperature_details() {
    log_info "Performing detailed temperature analysis..."
    
    local temp_data="$TEMP_DIR/temperature_data.txt"
    nvidia-smi --query-gpu=index,name,temperature.gpu,temperature.memory,fan.speed --format=csv,noheader,nounits > "$temp_data" 2>/dev/null || return 1
    
    local hot_gpus=0
    local overheated_gpus=0
    
    while IFS=',' read -r index name gpu_temp mem_temp fan_speed; do
        # 공백 제거
        index=$(echo "$index" | xargs)
        name=$(echo "$name" | xargs)
        gpu_temp=$(echo "$gpu_temp" | xargs)
        mem_temp=$(echo "$mem_temp" | xargs)
        fan_speed=$(echo "$fan_speed" | xargs)
        
        log_info "GPU $index thermal details:"
        log_info "  GPU Temperature: ${gpu_temp}°C"
        
        if [[ "$mem_temp" != "N/A" ]]; then
            log_info "  Memory Temperature: ${mem_temp}°C"
        fi
        
        if [[ "$fan_speed" != "N/A" ]]; then
            log_info "  Fan Speed: ${fan_speed}%"
        fi
        
        # 온도 상태 평가
        if [[ "$gpu_temp" != "N/A" ]] && [[ "$gpu_temp" =~ ^[0-9]+$ ]]; then
            if [[ $gpu_temp -ge $TEMP_CRITICAL_THRESHOLD ]]; then
                log_critical "GPU $index overheating: ${gpu_temp}°C"
                overheated_gpus=$((overheated_gpus + 1))
            elif [[ $gpu_temp -ge $TEMP_WARNING_THRESHOLD ]]; then
                log_warn "GPU $index running hot: ${gpu_temp}°C"
                hot_gpus=$((hot_gpus + 1))
            fi
        fi
        
    done < "$temp_data"
    
    check_results["temperature"]=$((2 - overheated_gpus - hot_gpus))
    
    if [[ $overheated_gpus -gt 0 ]]; then
        alerts["temperature"]="$overheated_gpus GPU(s) overheating"
    elif [[ $hot_gpus -gt 0 ]]; then
        alerts["temperature"]="$hot_gpus GPU(s) running hot"
    fi
}

# 메모리 상세 분석
check_memory_details() {
    log_info "Performing detailed memory analysis..."
    
    local mem_data="$TEMP_DIR/memory_data.txt"
    nvidia-smi --query-gpu=index,name,memory.used,memory.free,memory.total --format=csv,noheader,nounits > "$mem_data" 2>/dev/null || return 1
    
    local high_mem_gpus=0
    local total_memory=0
    local used_memory=0
    
    while IFS=',' read -r index name mem_used mem_free mem_total; do
        # 공백 제거
        index=$(echo "$index" | xargs)
        name=$(echo "$name" | xargs)
        mem_used=$(echo "$mem_used" | xargs)
        mem_free=$(echo "$mem_free" | xargs)
        mem_total=$(echo "$mem_total" | xargs)
        
        if [[ "$mem_used" != "N/A" && "$mem_total" != "N/A" && "$mem_total" -gt 0 ]]; then
            local mem_percent=$((mem_used * 100 / mem_total))
            
            log_info "GPU $index memory usage: ${mem_used}MB/${mem_total}MB (${mem_percent}%)"
            
            total_memory=$((total_memory + mem_total))
            used_memory=$((used_memory + mem_used))
            
            if [[ $mem_percent -ge $MEMORY_HIGH_THRESHOLD ]]; then
                log_warn "GPU $index high memory usage: ${mem_percent}%"
                high_mem_gpus=$((high_mem_gpus + 1))
            fi
        fi
        
    done < "$mem_data"
    
    # 전체 메모리 사용률
    if [[ $total_memory -gt 0 ]]; then
        local overall_mem_percent=$((used_memory * 100 / total_memory))
        log_info "Overall cluster memory usage: ${used_memory}MB/${total_memory}MB (${overall_mem_percent}%)"
    fi
    
    check_results["memory"]=$((2 - high_mem_gpus))
    
    if [[ $high_mem_gpus -gt 0 ]]; then
        alerts["memory"]="$high_mem_gpus GPU(s) with high memory usage"
    fi
}

# 프로세스 분석
check_gpu_processes() {
    log_info "Checking GPU processes..."
    
    local process_data="$TEMP_DIR/processes.txt"
    nvidia-smi pmon -c 1 > "$process_data" 2>/dev/null || return 1
    
    local process_count=0
    
    # 프로세스 헤더 건너뛰기
    tail -n +3 "$process_data" | while read -r line; do
        if [[ -n "$line" && ! "$line" =~ ^# ]]; then
            process_count=$((process_count + 1))
            log_info "Active GPU process: $line"
        fi
    done
    
    if [[ $process_count -eq 0 ]]; then
        log_info "No active GPU processes found"
    else
        log_info "Found $process_count active GPU process(es)"
    fi
}

# 시스템 리소스 확인
check_system_resources() {
    log_info "Checking system resources..."
    
    # CPU 사용률
    local cpu_usage=$(top -bn1 | grep "Cpu(s)" | awk '{print $2}' | awk -F'%' '{print $1}')
    log_info "CPU usage: ${cpu_usage}%"
    
    # 메모리 사용률
    local mem_info=$(free | grep Mem)
    local mem_total=$(echo $mem_info | awk '{print $2}')
    local mem_used=$(echo $mem_info | awk '{print $3}')
    local mem_percent=$((mem_used * 100 / mem_total))
    log_info "System memory usage: ${mem_percent}%"
    
    # 디스크 사용률
    local disk_usage=$(df / | tail -1 | awk '{print $5}' | sed 's/%//')
    log_info "Root disk usage: ${disk_usage}%"
    
    # 시스템 부하
    local load_avg=$(uptime | awk -F'load average:' '{print $2}')
    log_info "System load average: $load_avg"
    
    # 경고 조건 확인
    if [[ ${cpu_usage%.*} -gt 90 ]]; then
        log_warn "High CPU usage: ${cpu_usage}%"
        alerts["system"]="High CPU usage: ${cpu_usage}%"
    fi
    
    if [[ $mem_percent -gt 90 ]]; then
        log_warn "High memory usage: ${mem_percent}%"
        alerts["system"]="${alerts["system"]} High memory usage: ${mem_percent}%"
    fi
    
    if [[ $disk_usage -gt 90 ]]; then
        log_warn "High disk usage: ${disk_usage}%"
        alerts["system"]="${alerts["system"]} High disk usage: ${disk_usage}%"
    fi
}

# API를 통한 상세 상태 확인
check_api_gpu_status() {
    if [[ "${check_results["api_connection"]}" -eq 1 ]]; then
        log_info "Checking GPU status via API..."
        
        local devices_url="$API_BASE_URL/api/v1/gpu/devices"
        local overview_url="$API_BASE_URL/api/v1/gpu/overview"
        
        # 장비 상태 확인
        local devices_response=$(curl -s "$devices_url" 2>/dev/null)
        if [[ -n "$devices_response" && "$devices_response" != "null" ]]; then
            local device_count=$(echo "$devices_response" | jq '. | length' 2>/dev/null || echo "0")
            log_info "API reports $device_count GPU device(s)"
            
            # 문제가 있는 장비 확인
            local unhealthy_devices=$(echo "$devices_response" | jq '[.[] | select(.healthStatus != "HEALTHY")] | length' 2>/dev/null || echo "0")
            if [[ $unhealthy_devices -gt 0 ]]; then
                log_warn "API reports $unhealthy_devices unhealthy device(s)"
            fi
        fi
        
        # 클러스터 개요 확인
        local overview_response=$(curl -s "$overview_url" 2>/dev/null)
        if [[ -n "$overview_response" && "$overview_response" != "null" ]]; then
            local utilization=$(echo "$overview_response" | jq '.overallGpuUtilization' 2>/dev/null || echo "0")
            local alerts_count=$(echo "$overview_response" | jq '.totalAlerts' 2>/dev/null || echo "0")
            
            log_info "Cluster utilization: ${utilization}%"
            if [[ $alerts_count -gt 0 ]]; then
                log_warn "API reports $alerts_count active alert(s)"
            fi
        fi
    fi
}

# 자동 문제 해결 시도
attempt_fix_issues() {
    log_info "Attempting to fix detected issues..."
    
    local fixes_applied=0
    
    # 1. nvidia-smi 행이 걸린 경우 재시작 시도
    if [[ "${check_results["nvidia_smi"]}" -eq 0 ]]; then
        log_info "Attempting to restart NVIDIA services..."
        
        if systemctl is-active --quiet nvidia-persistenced; then
            sudo systemctl restart nvidia-persistenced && fixes_applied=$((fixes_applied + 1))
        fi
        
        # nvidia-ml 재시작 시도
        if lsmod | grep -q nvidia; then
            log_info "Reloading nvidia kernel modules..."
            sudo rmmod nvidia_uvm nvidia_drm nvidia_modeset nvidia 2>/dev/null || true
            sudo modprobe nvidia && fixes_applied=$((fixes_applied + 1))
        fi
    fi
    
    # 2. 과열된 GPU의 팬 속도 조정 시도
    for gpu_id in "${!device_status[@]}"; do
        if [[ "${device_status[$gpu_id]}" == "CRITICAL" ]]; then
            local gpu_index=${gpu_id#gpu_}
            log_info "Attempting to increase fan speed for GPU $gpu_index..."
            
            # 팬 속도를 100%로 설정 시도
            nvidia-smi -i "$gpu_index" -pl 300 2>/dev/null && fixes_applied=$((fixes_applied + 1))
        fi
    done
    
    # 3. 메모리 정리 시도
    if [[ "${alerts["memory"]}" ]]; then
        log_info "Attempting to clear GPU memory..."
        
        # 유휴 프로세스 정리
        nvidia-smi --gpu-reset 2>/dev/null && fixes_applied=$((fixes_applied + 1))
    fi
    
    # 4. API 서비스 재시작 시도
    if [[ "${check_results["api_connection"]}" -eq 0 ]]; then
        log_info "Attempting to restart API service..."
        
        if systemctl is-active --quiet k8s-monitor; then
            sudo systemctl restart k8s-monitor && fixes_applied=$((fixes_applied + 1))
        fi
    fi
    
    log_info "Applied $fixes_applied fix(es)"
    
    if [[ $fixes_applied -gt 0 ]]; then
        log_info "Waiting 10 seconds for changes to take effect..."
        sleep 10
        
        # 재검사
        log_info "Re-checking GPU status after fixes..."
        check_nvidia_smi
        check_gpu_devices
    fi
}

# 결과를 JSON 형식으로 출력
output_json_results() {
    local output_file="$1"
    
    cat > "$output_file" << EOF
{
  "timestamp": "$(date -Iseconds)",
  "overall_status": "$(get_overall_status)",
  "checks": {
    "nvidia_driver": ${check_results["nvidia_driver"]},
    "nvidia_smi": ${check_results["nvidia_smi"]},
    "api_connection": ${check_results["api_connection"]},
    "gpu_devices": ${check_results["gpu_devices"]},
    "temperature": ${check_results["temperature"]},
    "memory": ${check_results["memory"]}
  },
  "device_status": {
EOF

    local first=true
    for device in "${!device_status[@]}"; do
        if [[ "$first" == "true" ]]; then
            first=false
        else
            echo "," >> "$output_file"
        fi
        echo "    \"$device\": \"${device_status[$device]}\"" >> "$output_file"
    done

    cat >> "$output_file" << EOF
  },
  "alerts": {
EOF

    first=true
    for alert_type in "${!alerts[@]}"; do
        if [[ "$first" == "true" ]]; then
            first=false
        else
            echo "," >> "$output_file"
        fi
        echo "    \"$alert_type\": \"${alerts[$alert_type]}\"" >> "$output_file"
    done

    cat >> "$output_file" << EOF
  }
}
EOF
}

# 결과를 HTML 형식으로 출력
output_html_results() {
    local output_file="$1"
    local overall_status=$(get_overall_status)
    
    cat > "$output_file" << EOF
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>GPU Health Check Report</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .header { background: #f0f0f0; padding: 20px; border-radius: 5px; margin-bottom: 20px; }
        .status-healthy { color: #28a745; }
        .status-warning { color: #ffc107; }
        .status-critical { color: #dc3545; }
        .check-result { margin: 10px 0; padding: 10px; border-left: 4px solid #ccc; }
        .check-pass { border-left-color: #28a745; }
        .check-warn { border-left-color: #ffc107; }
        .check-fail { border-left-color: #dc3545; }
        .device-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 20px; }
        .device-card { border: 1px solid #ddd; padding: 15px; border-radius: 5px; }
        .alerts { background: #f8d7da; border: 1px solid #f5c6cb; padding: 15px; border-radius: 5px; margin-top: 20px; }
    </style>
</head>
<body>
    <div class="header">
        <h1>GPU Health Check Report</h1>
        <p><strong>Timestamp:</strong> $(date)</p>
        <p><strong>Overall Status:</strong> <span class="status-$overall_status">$overall_status</span></p>
    </div>
    
    <h2>Check Results</h2>
EOF

    # 체크 결과 추가
    for check in "${!check_results[@]}"; do
        local result=${check_results[$check]}
        local css_class=""
        local status_text=""
        
        case $result in
            2) css_class="check-pass"; status_text="PASS" ;;
            1) css_class="check-warn"; status_text="WARNING" ;;
            0) css_class="check-fail"; status_text="FAIL" ;;
            *) css_class=""; status_text="UNKNOWN" ;;
        esac
        
        echo "<div class=\"check-result $css_class\">" >> "$output_file"
        echo "<strong>$(echo $check | tr '_' ' ' | tr '[:lower:]' '[:upper:]'):</strong> $status_text" >> "$output_file"
        echo "</div>" >> "$output_file"
    done

    # 장비 상태 추가
    if [[ ${#device_status[@]} -gt 0 ]]; then
        cat >> "$output_file" << EOF
    
    <h2>Device Status</h2>
    <div class="device-grid">
EOF

        for device in "${!device_status[@]}"; do
            local status="${device_status[$device]}"
            echo "<div class=\"device-card\">" >> "$output_file"
            echo "<h3>$device</h3>" >> "$output_file"
            echo "<p><strong>Status:</strong> <span class=\"status-${status,,}\">$status</span></p>" >> "$output_file"
            echo "</div>" >> "$output_file"
        done
        
        echo "</div>" >> "$output_file"
    fi

    # 알람 추가
    if [[ ${#alerts[@]} -gt 0 ]]; then
        cat >> "$output_file" << EOF
    
    <div class="alerts">
        <h2>Active Alerts</h2>
EOF

        for alert_type in "${!alerts[@]}"; do
            echo "<p><strong>$alert_type:</strong> ${alerts[$alert_type]}</p>" >> "$output_file"
        done
        
        echo "</div>" >> "$output_file"
    fi

    cat >> "$output_file" << EOF
</body>
</html>
EOF
}

# 전체 상태 계산
get_overall_status() {
    local critical_count=0
    local warning_count=0
    
    for result in "${check_results[@]}"; do
        case $result in
            0) critical_count=$((critical_count + 1)) ;;
            1) warning_count=$((warning_count + 1)) ;;
        esac
    done
    
    if [[ $critical_count -gt 0 ]]; then
        echo "critical"
    elif [[ $warning_count -gt 0 ]]; then
        echo "warning"
    else
        echo "healthy"
    fi
}

# 이메일 전송
send_email_report() {
    local email="$1"
    local overall_status=$(get_overall_status)
    
    if command -v mail >/dev/null 2>&1; then
        local subject="GPU Health Check Report - Status: $(echo $overall_status | tr '[:lower:]' '[:upper:]')"
        
        {
            echo "GPU Health Check Report"
            echo "======================="
            echo "Timestamp: $(date)"
            echo "Overall Status: $overall_status"
            echo ""
            echo "Check Results:"
            for check in "${!check_results[@]}"; do
                local result=${check_results[$check]}
                local status_text=""
                case $result in
                    2) status_text="PASS" ;;
                    1) status_text="WARNING" ;;
                    0) status_text="FAIL" ;;
                    *) status_text="UNKNOWN" ;;
                esac
                echo "  $(echo $check | tr '_' ' '): $status_text"
            done
            
            if [[ ${#alerts[@]} -gt 0 ]]; then
                echo ""
                echo "Active Alerts:"
                for alert_type in "${!alerts[@]}"; do
                    echo "  $alert_type: ${alerts[$alert_type]}"
                done
            fi
        } | mail -s "$subject" "$email"
        
        log_info "Report sent to $email"
    else
        log_warn "Mail command not available, cannot send email report"
    fi
}

# Slack 알림 전송
send_slack_notification() {
    local webhook_url="$1"
    local overall_status=$(get_overall_status)
    
    local color=""
    case $overall_status in
        "healthy") color="good" ;;
        "warning") color="warning" ;;
        "critical") color="danger" ;;
    esac
    
    local payload=$(cat << EOF
{
    "attachments": [
        {
            "color": "$color",
            "title": "GPU Health Check Report",
            "fields": [
                {
                    "title": "Overall Status",
                    "value": "$(echo $overall_status | tr '[:lower:]' '[:upper:]')",
                    "short": true
                },
                {
                    "title": "Timestamp",
                    "value": "$(date)",
                    "short": true
                }
            ]
        }
    ]
}
EOF
)
    
    if curl -X POST -H 'Content-type: application/json' --data "$payload" "$webhook_url" >/dev/null 2>&1; then
        log_info "Slack notification sent"
    else
        log_warn "Failed to send Slack notification"
    fi
}

# 데몬 모드 실행
run_daemon() {
    local interval="$1"
    
    log_info "Starting GPU health check daemon (interval: ${interval}s)"
    
    while true; do
        log_info "Running scheduled health check..."
        
        # 체크 실행
        check_nvidia_driver
        check_nvidia_smi && {
            check_gpu_devices
            check_temperature_details
            check_memory_details
            check_gpu_processes
        }
        check_api_connection
        check_system_resources
        
        # 결과 평가
        local overall_status=$(get_overall_status)
        
        if [[ "$overall_status" != "healthy" ]]; then
            log_warn "Issues detected, sending notifications..."
            
            if [[ -n "$MAIL_TO" ]]; then
                send_email_report "$MAIL_TO"
            fi
            
            if [[ -n "$SLACK_WEBHOOK" ]]; then
                send_slack_notification "$SLACK_WEBHOOK"
            fi
        fi
        
        log_info "Waiting $interval seconds until next check..."
        sleep "$interval"
    done
}

# 메인 실행 함수
main() {
    local output_file=""
    local output_format="text"
    local verbose=false
    local quiet=false
    local fix_issues=false
    local daemon_mode=false
    local daemon_interval=300
    local skip_nvidia_smi=false
    local skip_api=false
    
    # 명령행 인자 파싱
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -v|--verbose)
                verbose=true
                shift
                ;;
            -q|--quiet)
                quiet=true
                shift
                ;;
            -o|--output)
                output_file="$2"
                shift 2
                ;;
            -f|--format)
                output_format="$2"
                shift 2
                ;;
            --api-url)
                API_BASE_URL="$2"
                shift 2
                ;;
            --temp-warning)
                TEMP_WARNING_THRESHOLD="$2"
                shift 2
                ;;
            --temp-critical)
                TEMP_CRITICAL_THRESHOLD="$2"
                shift 2
                ;;
            --mail-to)
                MAIL_TO="$2"
                shift 2
                ;;
            --slack-webhook)
                SLACK_WEBHOOK="$2"
                shift 2
                ;;
            --fix-issues)
                fix_issues=true
                shift
                ;;
            --daemon)
                daemon_mode=true
                shift
                ;;
            --interval)
                daemon_interval="$2"
                shift 2
                ;;
            --no-nvidia-smi)
                skip_nvidia_smi=true
                shift
                ;;
            --no-api)
                skip_api=true
                shift
                ;;
            *)
                log_error "Unknown option: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    # Quiet 모드 설정
    if [[ "$quiet" == "true" ]]; then
        exec 1>/dev/null
    fi
    
    log_info "Starting GPU health check..."
    log_info "Configuration:"
    log_info "  Temperature warning threshold: ${TEMP_WARNING_THRESHOLD}°C"
    log_info "  Temperature critical threshold: ${TEMP_CRITICAL_THRESHOLD}°C"
    log_info "  API base URL: $API_BASE_URL"
    
    # 초기화
    initialize
    
    # 데몬 모드
    if [[ "$daemon_mode" == "true" ]]; then
        run_daemon "$daemon_interval"
        exit 0
    fi
    
    # 정상 체크 실행
    check_nvidia_driver
    
    if [[ "$skip_nvidia_smi" != "true" ]]; then
        check_nvidia_smi && {
            check_gpu_devices
            check_temperature_details
            check_memory_details
            check_gpu_processes
        }
    fi
    
    if [[ "$skip_api" != "true" ]]; then
        check_api_connection
        check_api_gpu_status
    fi
    
    check_system_resources
    
    # 문제 해결 시도
    if [[ "$fix_issues" == "true" ]]; then
        attempt_fix_issues
    fi
    
    # 결과 출력
    local overall_status=$(get_overall_status)
    
    case $overall_status in
        "healthy")
            log_success "Overall GPU health status: HEALTHY"
            ;;
        "warning")
            log_warn "Overall GPU health status: WARNING"
            ;;
        "critical")
            log_critical "Overall GPU health status: CRITICAL"
            ;;
    esac
    
    # 파일 출력
    if [[ -n "$output_file" ]]; then
        case $output_format in
            "json")
                output_json_results "$output_file"
                log_info "JSON report saved to: $output_file"
                ;;
            "html")
                output_html_results "$output_file"
                log_info "HTML report saved to: $output_file"
                ;;
            *)
                # 텍스트 출력은 이미 로그로 되어있음
                log_info "Text report saved to log file: $LOG_FILE"
                ;;
        esac
    fi
    
    # 알림 전송
    if [[ -n "$MAIL_TO" ]]; then
        send_email_report "$MAIL_TO"
    fi
    
    if [[ -n "$SLACK_WEBHOOK" ]]; then
        send_slack_notification "$SLACK_WEBHOOK"
    fi
    
    # 종료 코드 설정
    case $overall_status in
        "healthy") exit 0 ;;
        "warning") exit 1 ;;
        "critical") exit 2 ;;
    esac
}

# 정리 함수
cleanup() {
    if [[ -d "$TEMP_DIR" ]]; then
        rm -rf "$TEMP_DIR"
    fi
}

# 신호 처리
trap cleanup EXIT

# 스크립트 실행
main "$@"