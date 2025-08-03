#!/bin/bash

# GPU 관리 시스템 로그 순환 스크립트
# 애플리케이션 로그, GPU 메트릭 로그, 시스템 로그 관리
# 작성자: GPU Management System
# 버전: 1.0.0

set -euo pipefail

# ===========================================
# 설정 변수
# ===========================================

# 스크립트 기본 설정
SCRIPT_NAME="$(basename "$0")"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_FILE="${LOG_FILE:-/var/log/gpu-management/log-rotation.log}"
CONFIG_FILE="${CONFIG_FILE:-/etc/gpu-management/log-rotation.conf}"

# 로그 디렉토리 설정
GPU_LOG_DIR="${GPU_LOG_DIR:-/var/log/gpu-management}"
APP_LOG_DIR="${APP_LOG_DIR:-/var/log/k8s-monitor}"
NGINX_LOG_DIR="${NGINX_LOG_DIR:-/var/log/nginx}"
SYSTEMD_LOG_DIR="${SYSTEMD_LOG_DIR:-/var/log/journal}"

# 기본 순환 설정
DEFAULT_MAX_SIZE="${DEFAULT_MAX_SIZE:-100M}"
DEFAULT_MAX_AGE="${DEFAULT_MAX_AGE:-30}"
DEFAULT_MAX_FILES="${DEFAULT_MAX_FILES:-10}"
COMPRESSION_TYPE="${COMPRESSION_TYPE:-gzip}"
COMPRESSION_DELAY="${COMPRESSION_DELAY:-1}"

# 알림 설정
NOTIFICATION_ENABLED="${NOTIFICATION_ENABLED:-false}"
SLACK_WEBHOOK_URL="${SLACK_WEBHOOK_URL:-}"
EMAIL_RECIPIENTS="${EMAIL_RECIPIENTS:-}"

# 특별 처리가 필요한 로그들
SPECIAL_LOGS="${SPECIAL_LOGS:-}"

# ===========================================
# 로그 설정별 정의
# ===========================================

# 로그 타입별 설정
declare -A LOG_CONFIGS=(
    # GPU 관리 로그
    ["gpu-management"]="$GPU_LOG_DIR/gpu-management.log:50M:15:5"
    ["gpu-metrics"]="$GPU_LOG_DIR/gpu-metrics.log:200M:30:10"
    ["gpu-allocation"]="$GPU_LOG_DIR/gpu-allocation.log:30M:15:5"
    ["gpu-mig"]="$GPU_LOG_DIR/gpu-mig.log:20M:15:5"
    ["gpu-cost"]="$GPU_LOG_DIR/gpu-cost.log:20M:30:7"
    ["metrics-validation"]="$GPU_LOG_DIR/metrics-validation.log:10M:7:3"
    ["db-backup"]="$GPU_LOG_DIR/db-backup.log:50M:90:12"
    
    # 애플리케이션 로그
    ["k8s-monitor"]="$APP_LOG_DIR/k8s-monitor.log:100M:30:10"
    ["spring-boot"]="$APP_LOG_DIR/spring.log:100M:15:7"
    ["access"]="$APP_LOG_DIR/access.log:500M:30:15"
    ["error"]="$APP_LOG_DIR/error.log:100M:60:12"
    
    # 웹서버 로그
    ["nginx-access"]="$NGINX_LOG_DIR/access.log:1G:30:15"
    ["nginx-error"]="$NGINX_LOG_DIR/error.log:100M:60:12"
    
    # 시스템 로그
    ["system"]="/var/log/syslog:500M:30:10"
    ["auth"]="/var/log/auth.log:100M:90:15"
    ["kern"]="/var/log/kern.log:100M:30:10"
)

# ===========================================
# 로깅 함수
# ===========================================

setup_logging() {
    local log_dir
    log_dir="$(dirname "$LOG_FILE")"
    mkdir -p "$log_dir"
    
    # 로그 헤더 작성
    {
        echo "=============================================="
        echo "GPU Management Log Rotation"
        echo "Started: $(date '+%Y-%m-%d %H:%M:%S')"
        echo "Script: $SCRIPT_NAME"
        echo "PID: $"
        echo "=============================================="
    } >> "$LOG_FILE"
}

log() {
    local level="$1"
    shift
    local message="$*"
    local timestamp
    timestamp="$(date '+%Y-%m-%d %H:%M:%S')"
    
    echo "[$timestamp] [$level] $message" | tee -a "$LOG_FILE"
}

log_info() {
    log "INFO" "$@"
}

log_warn() {
    log "WARN" "$@"
}

log_error() {
    log "ERROR" "$@"
}

log_debug() {
    if [[ "${DEBUG:-false}" == "true" ]]; then
        log "DEBUG" "$@"
    fi
}

# ===========================================
# 설정 로드 함수
# ===========================================

load_config() {
    if [[ -f "$CONFIG_FILE" ]]; then
        log_info "Loading configuration from $CONFIG_FILE"
        # shellcheck source=/dev/null
        source "$CONFIG_FILE"
    else
        log_warn "Configuration file not found: $CONFIG_FILE, using defaults"
    fi
}

# ===========================================
# 유틸리티 함수
# ===========================================

parse_size() {
    local size="$1"
    local number="${size%[A-Za-z]*}"
    local unit="${size#$number}"
    
    case "${unit^^}" in
        B|"") echo "$number" ;;
        K|KB) echo $((number * 1024)) ;;
        M|MB) echo $((number * 1024 * 1024)) ;;
        G|GB) echo $((number * 1024 * 1024 * 1024)) ;;
        T|TB) echo $((number * 1024 * 1024 * 1024 * 1024)) ;;
        *) 
            log_error "Invalid size unit: $unit"
            return 1
            ;;
    esac
}

get_file_size() {
    local file="$1"
    if [[ -f "$file" ]]; then
        stat -f%z "$file" 2>/dev/null || stat -c%s "$file" 2>/dev/null || echo 0
    else
        echo 0
    fi
}

get_file_age_days() {
    local file="$1"
    if [[ -f "$file" ]]; then
        local file_time
        file_time="$(stat -f%m "$file" 2>/dev/null || stat -c%Y "$file" 2>/dev/null || echo 0)"
        echo $(( ($(date +%s) - file_time) / 86400 ))
    else
        echo 999
    fi
}

humanize_size() {
    local size="$1"
    if [[ $size -ge $((1024*1024*1024*1024)) ]]; then
        echo "$(( size / (1024*1024*1024*1024) ))TB"
    elif [[ $size -ge $((1024*1024*1024)) ]]; then
        echo "$(( size / (1024*1024*1024) ))GB"
    elif [[ $size -ge $((1024*1024)) ]]; then
        echo "$(( size / (1024*1024) ))MB"
    elif [[ $size -ge 1024 ]]; then
        echo "$(( size / 1024 ))KB"
    else
        echo "${size}B"
    fi
}

# ===========================================
# 압축 함수
# ===========================================

compress_log() {
    local log_file="$1"
    local compressed_file=""
    
    case "$COMPRESSION_TYPE" in
        gzip)
            compressed_file="${log_file}.gz"
            if gzip -9 "$log_file"; then
                log_debug "Compressed: $log_file -> $compressed_file"
                return 0
            else
                log_error "Failed to compress: $log_file"
                return 1
            fi
            ;;
        xz)
            compressed_file="${log_file}.xz"
            if xz -9 "$log_file"; then
                log_debug "Compressed: $log_file -> $compressed_file"
                return 0
            else
                log_error "Failed to compress: $log_file"
                return 1
            fi
            ;;
        bzip2)
            compressed_file="${log_file}.bz2"
            if bzip2 -9 "$log_file"; then
                log_debug "Compressed: $log_file -> $compressed_file"
                return 0
            else
                log_error "Failed to compress: $log_file"
                return 1
            fi
            ;;
        none)
            log_debug "No compression applied to: $log_file"
            return 0
            ;;
        *)
            log_error "Unsupported compression type: $COMPRESSION_TYPE"
            return 1
            ;;
    esac
}

# ===========================================
# 로그 순환 함수
# ===========================================

rotate_log_file() {
    local log_file="$1"
    local max_size="$2"
    local max_age="$3"
    local max_files="$4"
    
    log_debug "Processing log file: $log_file"
    
    # 파일 존재 확인
    if [[ ! -f "$log_file" ]]; then
        log_debug "Log file does not exist: $log_file"
        return 0
    fi
    
    # 크기 확인
    local current_size
    current_size="$(get_file_size "$log_file")"
    local max_size_bytes
    max_size_bytes="$(parse_size "$max_size")"
    
    local need_rotation=false
    local rotation_reason=""
    
    # 크기 기준 확인
    if [[ $current_size -gt $max_size_bytes ]]; then
        need_rotation=true
        rotation_reason="size ($(humanize_size $current_size) > $max_size)"
    fi
    
    # 나이 기준 확인
    local file_age_days
    file_age_days="$(get_file_age_days "$log_file")"
    if [[ $file_age_days -gt $max_age ]]; then
        need_rotation=true
        if [[ -n "$rotation_reason" ]]; then
            rotation_reason="$rotation_reason and age (${file_age_days}d > ${max_age}d)"
        else
            rotation_reason="age (${file_age_days}d > ${max_age}d)"
        fi
    fi
    
    if [[ "$need_rotation" == "false" ]]; then
        log_debug "No rotation needed for: $log_file"
        return 0
    fi
    
    log_info "Rotating log file: $log_file (reason: $rotation_reason)"
    
    # 기존 순환된 로그들 시프트
    local i
    for (( i=max_files-1; i>=1; i-- )); do
        local old_file="${log_file}.$i"
        local new_file="${log_file}.$((i+1))"
        
        # 압축된 파일들 처리
        for ext in "" ".gz" ".xz" ".bz2"; do
            if [[ -f "${old_file}${ext}" ]]; then
                if [[ $i -eq $((max_files-1)) ]]; then
                    # 마지막 파일은 삭제
                    rm -f "${old_file}${ext}"
                    log_debug "Removed old log: ${old_file}${ext}"
                else
                    # 파일 이동
                    mv "${old_file}${ext}" "${new_file}${ext}"
                    log_debug "Moved: ${old_file}${ext} -> ${new_file}${ext}"
                fi
                break
            fi
        done
    done
    
    # 현재 로그 파일을 .1로 이동
    local rotated_file="${log_file}.1"
    if mv "$log_file" "$rotated_file"; then
        log_info "Rotated: $log_file -> $rotated_file"
        
        # 새 로그 파일 생성 (권한 유지)
        touch "$log_file"
        
        # 원본 파일의 권한과 소유자 복사
        if [[ -f "$rotated_file" ]]; then
            chown --reference="$rotated_file" "$log_file" 2>/dev/null || true
            chmod --reference="$rotated_file" "$log_file" 2>/dev/null || true
        fi
        
        # 압축 (지연 압축 고려)
        if [[ "$COMPRESSION_DELAY" -eq 0 ]]; then
            compress_log "$rotated_file"
        else
            # 지연 압축을 위한 스케줄링
            schedule_delayed_compression "$rotated_file"
        fi
        
        # 서비스 재시작이 필요한 로그인지 확인
        restart_service_if_needed "$log_file"
        
        return 0
    else
        log_error "Failed to rotate log file: $log_file"
        return 1
    fi
}

schedule_delayed_compression() {
    local log_file="$1"
    
    # at 명령어를 사용하여 지연 압축 스케줄링
    if command -v at >/dev/null; then
        echo "$(dirname "$0")/$(basename "$0") --compress-file '$log_file'" | at "now + $COMPRESSION_DELAY days" 2>/dev/null || true
        log_debug "Scheduled delayed compression for: $log_file"
    else
        log_warn "at command not available, compressing immediately: $log_file"
        compress_log "$log_file"
    fi
}

restart_service_if_needed() {
    local log_file="$1"
    local service=""
    
    # 로그 파일에 따른 서비스 결정
    case "$log_file" in
        */nginx/*)
            service="nginx"
            ;;
        */gpu-management/*)
            service="gpu-management"
            ;;
        */k8s-monitor/*)
            service="k8s-monitor"
            ;;
    esac
    
    if [[ -n "$service" ]] && systemctl is-active "$service" >/dev/null 2>&1; then
        log_info "Sending SIGUSR1 to $service for log reopening"
        systemctl kill -s USR1 "$service" 2>/dev/null || {
            log_warn "Failed to send signal to $service, attempting reload"
            systemctl reload "$service" 2>/dev/null || true
        }
    fi
}

# ===========================================
# 특별 로그 처리 함수
# ===========================================

rotate_systemd_journal() {
    log_info "Rotating systemd journal logs"
    
    # journalctl을 사용한 로그 정리
    if command -v journalctl >/dev/null; then
        # 30일 이상된 로그 삭제
        journalctl --vacuum-time=30d >/dev/null 2>&1 || true
        
        # 최대 크기 제한 (1GB)
        journalctl --vacuum-size=1G >/dev/null 2>&1 || true
        
        log_info "Systemd journal rotation completed"
    else
        log_warn "journalctl not available, skipping systemd journal rotation"
    fi
}

rotate_application_logs() {
    log_info "Rotating application-specific logs"
    
    # Spring Boot 로그 특별 처리
    local spring_log_pattern="/var/log/k8s-monitor/spring*.log"
    for log_file in $spring_log_pattern; do
        if [[ -f "$log_file" ]]; then
            rotate_log_file "$log_file" "$DEFAULT_MAX_SIZE" "$DEFAULT_MAX_AGE" "$DEFAULT_MAX_FILES"
        fi
    done
    
    # GPU 메트릭 로그 특별 처리 (더 큰 용량 허용)
    local gpu_metrics_pattern="/var/log/gpu-management/metrics-*.log"
    for log_file in $gpu_metrics_pattern; do
        if [[ -f "$log_file" ]]; then
            rotate_log_file "$log_file" "500M" "7" "5"
        fi
    done
}

rotate_security_logs() {
    log_info "Rotating security-related logs"
    
    # 보안 관련 로그는 더 오래 보관
    local security_logs=(
        "/var/log/auth.log"
        "/var/log/secure"
        "/var/log/audit/audit.log"
    )
    
    for log_file in "${security_logs[@]}"; do
        if [[ -f "$log_file" ]]; then
            rotate_log_file "$log_file" "100M" "90" "12"
        fi
    done
}

# ===========================================
# 정리 함수
# ===========================================

cleanup_old_rotated_logs() {
    log_info "Cleaning up old rotated logs"
    
    local total_cleaned=0
    local total_size_freed=0
    
    # 각 로그 디렉토리에서 오래된 순환 로그 정리
    local log_dirs=(
        "$GPU_LOG_DIR"
        "$APP_LOG_DIR"
        "$NGINX_LOG_DIR"
        "/var/log"
    )
    
    for log_dir in "${log_dirs[@]}"; do
        if [[ ! -d "$log_dir" ]]; then
            continue
        fi
        
        log_debug "Cleaning up directory: $log_dir"
        
        # 30일 이상된 압축 로그 파일 찾기
        while IFS= read -r -d '' log_file; do
            local file_age_days
            file_age_days="$(get_file_age_days "$log_file")"
            
            if [[ $file_age_days -gt 30 ]]; then
                local file_size
                file_size="$(get_file_size "$log_file")"
                
                if rm "$log_file"; then
                    log_debug "Removed old log: $log_file (${file_age_days}d, $(humanize_size $file_size))"
                    ((total_cleaned++))
                    ((total_size_freed += file_size))
                fi
            fi
        done < <(find "$log_dir" -name "*.log.*" -type f \( -name "*.gz" -o -name "*.xz" -o -name "*.bz2" \) -print0 2>/dev/null)
    done
    
    if [[ $total_cleaned -gt 0 ]]; then
        log_info "Cleanup completed: $total_cleaned files removed, $(humanize_size $total_size_freed) freed"
    else
        log_info "No old logs to clean up"
    fi
}

cleanup_empty_log_dirs() {
    log_info "Cleaning up empty log directories"
    
    local log_dirs=(
        "$GPU_LOG_DIR"
        "$APP_LOG_DIR"
    )
    
    for log_dir in "${log_dirs[@]}"; do
        if [[ -d "$log_dir" ]]; then
            # 빈 하위 디렉토리 제거
            find "$log_dir" -type d -empty -delete 2>/dev/null || true
        fi
    done
}

# ===========================================
# 통계 및 보고 함수
# ===========================================

generate_rotation_report() {
    log_info "Generating log rotation report"
    
    local report_file="/tmp/log-rotation-report-$(date +%Y%m%d_%H%M%S).txt"
    
    {
        echo "GPU Management Log Rotation Report"
        echo "Generated: $(date '+%Y-%m-%d %H:%M:%S')"
        echo "Host: $(hostname)"
        echo ""
        
        echo "=== Log Directory Statistics ==="
        for log_dir in "$GPU_LOG_DIR" "$APP_LOG_DIR" "$NGINX_LOG_DIR"; do
            if [[ -d "$log_dir" ]]; then
                echo "Directory: $log_dir"
                echo "  Total files: $(find "$log_dir" -type f -name "*.log*" | wc -l)"
                echo "  Total size: $(du -sh "$log_dir" 2>/dev/null | cut -f1)"
                echo "  Active logs: $(find "$log_dir" -name "*.log" -not -name "*.log.*" | wc -l)"
                echo "  Rotated logs: $(find "$log_dir" -name "*.log.*" | wc -l)"
                echo ""
            fi
        done
        
        echo "=== Largest Log Files ==="
        find "$GPU_LOG_DIR" "$APP_LOG_DIR" -name "*.log" -type f -exec ls -lh {} \; 2>/dev/null | 
            sort -k5 -hr | head -10 | awk '{print $9 " (" $5 ")"}'
        echo ""
        
        echo "=== Oldest Rotated Logs ==="
        find "$GPU_LOG_DIR" "$APP_LOG_DIR" -name "*.log.*" -type f -exec ls -lt {} \; 2>/dev/null | 
            head -10 | awk '{print $9 " (" $6 " " $7 " " $8 ")"}'
        echo ""
        
        echo "=== Configuration ==="
        echo "  Default max size: $DEFAULT_MAX_SIZE"
        echo "  Default max age: $DEFAULT_MAX_AGE days"
        echo "  Default max files: $DEFAULT_MAX_FILES"
        echo "  Compression: $COMPRESSION_TYPE"
        echo "  Compression delay: $COMPRESSION_DELAY days"
        
    } > "$report_file"
    
    log_info "Report generated: $report_file"
    
    # 보고서를 로그에도 추가
    cat "$report_file" >> "$LOG_FILE"
    
    return 0
}

# ===========================================
# 알림 함수
# ===========================================

send_notification() {
    local status="$1"
    local message="$2"
    local details="${3:-}"
    
    if [[ "$NOTIFICATION_ENABLED" != "true" ]]; then
        return 0
    fi
    
    local timestamp
    timestamp="$(date '+%Y-%m-%d %H:%M:%S')"
    
    # Slack 알림
    if [[ -n "$SLACK_WEBHOOK_URL" ]]; then
        local color="good"
        if [[ "$status" != "SUCCESS" ]]; then
            color="warning"
        fi
        
        local payload
        payload=$(cat <<EOF
{
    "attachments": [
        {
            "color": "$color",
            "title": "GPU Management Log Rotation - $status",
            "text": "$message",
            "fields": [
                {
                    "title": "Server",
                    "value": "$(hostname)",
                    "short": true
                },
                {
                    "title": "Timestamp",
                    "value": "$timestamp",
                    "short": true
                }
            ]
        }
    ]
}
EOF
        )
        
        if [[ -n "$details" ]]; then
            payload=$(echo "$payload" | jq --arg details "$details" '.attachments[0].fields += [{"title": "Details", "value": $details, "short": false}]')
        fi
        
        curl -X POST -H 'Content-type: application/json' \
             --data "$payload" \
             --silent \
             --max-time 10 \
             "$SLACK_WEBHOOK_URL" >/dev/null || true
    fi
    
    # 이메일 알림
    if [[ -n "$EMAIL_RECIPIENTS" ]] && command -v mail >/dev/null; then
        local subject="GPU Management Log Rotation - $status"
        
        local email_body
        email_body=$(cat <<EOF
GPU Management Log Rotation Report

Status: $status
Message: $message
Server: $(hostname)
Timestamp: $timestamp

$details

Log file: $LOG_FILE
EOF
        )
        
        echo "$email_body" | mail -s "$subject" $EMAIL_RECIPIENTS || true
    fi
}

# ===========================================
# 메인 로그 순환 함수
# ===========================================

perform_log_rotation() {
    log_info "Starting log rotation process"
    
    local rotation_count=0
    local error_count=0
    
    # 정의된 로그 설정에 따른 순환
    for log_name in "${!LOG_CONFIGS[@]}"; do
        local config="${LOG_CONFIGS[$log_name]}"
        IFS=':' read -r log_path max_size max_age max_files <<< "$config"
        
        log_debug "Processing $log_name: $log_path"
        
        if rotate_log_file "$log_path" "$max_size" "$max_age" "$max_files"; then
            ((rotation_count++))
        else
            ((error_count++))
        fi
    done
    
    # 특별 로그 처리
    rotate_systemd_journal
    rotate_application_logs
    rotate_security_logs
    
    # 정리 작업
    cleanup_old_rotated_logs
    cleanup_empty_log_dirs
    
    log_info "Log rotation completed: $rotation_count rotations, $error_count errors"
    
    return $error_count
}

# ===========================================
# 사용법 표시
# ===========================================

show_usage() {
    cat <<EOF
Usage: $SCRIPT_NAME [OPTIONS]

GPU Management Log Rotation Script

OPTIONS:
    -h, --help              Show this help message
    -c, --config FILE       Configuration file path (default: $CONFIG_FILE)
    --compress-file FILE    Compress specific log file (for delayed compression)
    --cleanup-only          Only perform cleanup of old logs
    --report-only           Generate rotation report only
    --dry-run               Show what would be done without executing
    --debug                 Enable debug logging
    -v, --verbose           Verbose output

CONFIGURATION:
    Log rotation can be configured via:
    1. Command line options
    2. Configuration file ($CONFIG_FILE)
    3. Environment variables

    Example configuration file:
    DEFAULT_MAX_SIZE=100M
    DEFAULT_MAX_AGE=30
    DEFAULT_MAX_FILES=10
    COMPRESSION_TYPE=gzip
    COMPRESSION_DELAY=1
    NOTIFICATION_ENABLED=true
    SLACK_WEBHOOK_URL=https://hooks.slack.com/...

EXAMPLES:
    $SCRIPT_NAME                    # Normal log rotation
    $SCRIPT_NAME --cleanup-only     # Only cleanup old logs
    $SCRIPT_NAME --report-only      # Generate report only
    $SCRIPT_NAME --dry-run          # Show what would be done
EOF
}

# ===========================================
# 메인 함수
# ===========================================

main() {
    local compress_file=""
    local cleanup_only=false
    local report_only=false
    local dry_run=false
    
    # 명령행 인자 파싱
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_usage
                exit 0
                ;;
            -c|--config)
                CONFIG_FILE="$2"
                shift 2
                ;;
            --compress-file)
                compress_file="$2"
                shift 2
                ;;
            --cleanup-only)
                cleanup_only=true
                shift
                ;;
            --report-only)
                report_only=true
                shift
                ;;
            --dry-run)
                dry_run=true
                shift
                ;;
            --debug)
                DEBUG=true
                shift
                ;;
            -v|--verbose)
                set -x
                shift
                ;;
            *)
                log_error "Unknown option: $1"
                show_usage
                exit 1
                ;;
        esac
    done
    
    # 로깅 설정
    setup_logging
    
    # 설정 로드
    load_config
    
    log_info "Starting GPU Management log rotation"
    
    # 특별 작업 모드
    if [[ -n "$compress_file" ]]; then
        log_info "Compressing file: $compress_file"
        if [[ -f "$compress_file" ]]; then
            compress_log "$compress_file"
            exit $?
        else
            log_error "File not found: $compress_file"
            exit 1
        fi
    fi
    
    if [[ "$report_only" == "true" ]]; then
        generate_rotation_report
        exit 0
    fi
    
    if [[ "$dry_run" == "true" ]]; then
        log_info "DRY RUN MODE - No actual rotation will be performed"
        for log_name in "${!LOG_CONFIGS[@]}"; do
            local config="${LOG_CONFIGS[$log_name]}"
            IFS=':' read -r log_path max_size max_age max_files <<< "$config"
            log_info "Would process: $log_path (max_size: $max_size, max_age: ${max_age}d, max_files: $max_files)"
        done
        exit 0
    fi
    
    if [[ "$cleanup_only" == "true" ]]; then
        log_info "Cleanup only mode"
        cleanup_old_rotated_logs
        cleanup_empty_log_dirs
        exit 0
    fi
    
    # 메인 로그 순환 실행
    if perform_log_rotation; then
        log_info "Log rotation completed successfully"
        
        # 보고서 생성
        generate_rotation_report
        
        # 성공 알림
        send_notification "SUCCESS" "Log rotation completed successfully"
        
        exit 0
    else
        log_error "Log rotation completed with errors"
        
        # 실패 알림
        send_notification "FAILED" "Log rotation completed with errors - check logs for details"
        
        exit 1
    fi
}

# 스크립트 실행
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi