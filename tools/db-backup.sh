#!/bin/bash

# GPU 관리 시스템 데이터베이스 백업 스크립트
# 지원 DB: PostgreSQL, Oracle, H2
# 작성자: GPU Management System
# 버전: 1.0.0

set -euo pipefail

# ===========================================
# 설정 변수
# ===========================================

# 스크립트 기본 설정
SCRIPT_NAME="$(basename "$0")"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_FILE="${LOG_FILE:-/var/log/gpu-management/db-backup.log}"
CONFIG_FILE="${CONFIG_FILE:-/etc/gpu-management/db-backup.conf}"

# 백업 기본 설정
BACKUP_DIR="${BACKUP_DIR:-/var/backups/gpu-management}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
COMPRESSION="${COMPRESSION:-gzip}"
PARALLEL_JOBS="${PARALLEL_JOBS:-4}"
VERIFY_BACKUP="${VERIFY_BACKUP:-true}"

# 데이터베이스 연결 설정 (기본값)
DB_TYPE="${DB_TYPE:-postgresql}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-gpu_management}"
DB_USER="${DB_USER:-gpu_admin}"
DB_PASSWORD="${DB_PASSWORD:-}"
DB_SERVICE_NAME="${DB_SERVICE_NAME:-ORCL}"

# 알림 설정
NOTIFICATION_ENABLED="${NOTIFICATION_ENABLED:-false}"
SLACK_WEBHOOK_URL="${SLACK_WEBHOOK_URL:-}"
EMAIL_RECIPIENTS="${EMAIL_RECIPIENTS:-}"

# 백업 타입 (full, incremental, differential)
BACKUP_TYPE="${BACKUP_TYPE:-full}"

# ===========================================
# 로깅 함수
# ===========================================

setup_logging() {
    # 로그 디렉토리 생성
    local log_dir
    log_dir="$(dirname "$LOG_FILE")"
    mkdir -p "$log_dir"
    
    # 로그 파일 로테이션 (10MB 초과시)
    if [[ -f "$LOG_FILE" ]] && [[ $(stat -f%z "$LOG_FILE" 2>/dev/null || stat -c%s "$LOG_FILE" 2>/dev/null || echo 0) -gt 10485760 ]]; then
        mv "$LOG_FILE" "${LOG_FILE}.old"
    fi
    
    # 로그 헤더 작성
    {
        echo "=============================================="
        echo "GPU Management Database Backup"
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

check_dependencies() {
    local missing_deps=()
    
    case "$DB_TYPE" in
        postgresql)
            if ! command -v pg_dump >/dev/null; then
                missing_deps+=("postgresql-client")
            fi
            ;;
        oracle)
            if ! command -v expdp >/dev/null && ! command -v exp >/dev/null; then
                missing_deps+=("oracle-client")
            fi
            ;;
        h2)
            if ! command -v java >/dev/null; then
                missing_deps+=("java")
            fi
            ;;
    esac
    
    if [[ "$COMPRESSION" == "gzip" ]] && ! command -v gzip >/dev/null; then
        missing_deps+=("gzip")
    fi
    
    if [[ "$COMPRESSION" == "xz" ]] && ! command -v xz >/dev/null; then
        missing_deps+=("xz-utils")
    fi
    
    if [[ ${#missing_deps[@]} -gt 0 ]]; then
        log_error "Missing dependencies: ${missing_deps[*]}"
        return 1
    fi
    
    return 0
}

create_backup_directory() {
    local backup_date
    backup_date="$(date '+%Y%m%d')"
    BACKUP_PATH="$BACKUP_DIR/$backup_date"
    
    if ! mkdir -p "$BACKUP_PATH"; then
        log_error "Failed to create backup directory: $BACKUP_PATH"
        return 1
    fi
    
    log_info "Backup directory created: $BACKUP_PATH"
    return 0
}

get_backup_filename() {
    local timestamp
    local extension=""
    
    timestamp="$(date '+%Y%m%d_%H%M%S')"
    
    case "$COMPRESSION" in
        gzip) extension=".gz" ;;
        xz) extension=".xz" ;;
        *) extension="" ;;
    esac
    
    echo "${BACKUP_PATH}/gpu_management_${DB_TYPE}_${BACKUP_TYPE}_${timestamp}.sql${extension}"
}

# ===========================================
# 데이터베이스별 백업 함수
# ===========================================

backup_postgresql() {
    local backup_file="$1"
    local pg_dump_opts=()
    
    log_info "Starting PostgreSQL backup..."
    
    # 백업 옵션 설정
    pg_dump_opts+=(
        "--host=$DB_HOST"
        "--port=$DB_PORT" 
        "--username=$DB_USER"
        "--dbname=$DB_NAME"
        "--verbose"
        "--no-password"
        "--format=custom"
        "--compress=9"
    )
    
    if [[ "$BACKUP_TYPE" == "full" ]]; then
        pg_dump_opts+=("--clean" "--create")
    fi
    
    if [[ "$PARALLEL_JOBS" -gt 1 ]]; then
        pg_dump_opts+=("--jobs=$PARALLEL_JOBS")
    fi
    
    # 환경변수 설정
    export PGPASSWORD="$DB_PASSWORD"
    
    # 백업 실행
    local temp_file="${backup_file%.gz}.tmp"
    
    if pg_dump "${pg_dump_opts[@]}" > "$temp_file"; then
        log_info "PostgreSQL dump completed successfully"
        
        # 압축
        if [[ "$COMPRESSION" == "gzip" ]]; then
            if gzip -9 "$temp_file"; then
                mv "${temp_file}.gz" "$backup_file"
                log_info "Backup compressed successfully"
            else
                log_error "Failed to compress backup"
                return 1
            fi
        else
            mv "$temp_file" "$backup_file"
        fi
        
        return 0
    else
        log_error "PostgreSQL backup failed"
        rm -f "$temp_file"
        return 1
    fi
}

backup_oracle() {
    local backup_file="$1"
    local exp_opts=()
    
    log_info "Starting Oracle backup..."
    
    # Oracle 백업 디렉토리 설정
    local oracle_backup_dir="/var/backups/oracle"
    mkdir -p "$oracle_backup_dir"
    
    local dump_file="gpu_management_$(date '+%Y%m%d_%H%M%S').dmp"
    local log_file="gpu_management_$(date '+%Y%m%d_%H%M%S').log"
    
    # Data Pump Export 사용 (expdp)
    if command -v expdp >/dev/null; then
        log_info "Using Oracle Data Pump Export (expdp)"
        
        expdp_opts+=(
            "$DB_USER/$DB_PASSWORD@$DB_HOST:$DB_PORT/$DB_SERVICE_NAME"
            "directory=DATA_PUMP_DIR"
            "dumpfile=$dump_file"
            "logfile=$log_file"
            "schemas=$DB_USER"
            "compression=all"
        )
        
        if [[ "$PARALLEL_JOBS" -gt 1 ]]; then
            expdp_opts+=("parallel=$PARALLEL_JOBS")
        fi
        
        if expdp "${expdp_opts[@]}"; then
            # 백업 파일을 지정된 위치로 이동
            if [[ -f "/u01/app/oracle/admin/orcl/dpdump/$dump_file" ]]; then
                mv "/u01/app/oracle/admin/orcl/dpdump/$dump_file" "$backup_file"
                log_info "Oracle backup completed successfully"
                return 0
            else
                log_error "Oracle dump file not found"
                return 1
            fi
        else
            log_error "Oracle Data Pump Export failed"
            return 1
        fi
    else
        # 전통적인 Export 사용 (exp)
        log_warn "Data Pump Export not available, using traditional export"
        
        exp_opts+=(
            "$DB_USER/$DB_PASSWORD@$DB_HOST:$DB_PORT/$DB_SERVICE_NAME"
            "file=$backup_file"
            "log=${backup_file%.dmp}.log"
            "owner=$DB_USER"
            "compress=y"
        )
        
        if exp "${exp_opts[@]}"; then
            log_info "Oracle export completed successfully"
            return 0
        else
            log_error "Oracle export failed"
            return 1
        fi
    fi
}

backup_h2() {
    local backup_file="$1"
    
    log_info "Starting H2 backup..."
    
    # H2 데이터베이스 파일 경로 찾기
    local h2_db_path="${H2_DB_PATH:-/var/lib/gpu-management/h2}"
    
    if [[ ! -d "$h2_db_path" ]]; then
        log_error "H2 database directory not found: $h2_db_path"
        return 1
    fi
    
    # H2 백업 (파일 복사 방식)
    local temp_dir
    temp_dir="$(mktemp -d)"
    
    if cp -r "$h2_db_path"/* "$temp_dir/"; then
        # 백업 아카이브 생성
        if tar -czf "$backup_file" -C "$temp_dir" .; then
            log_info "H2 backup completed successfully"
            rm -rf "$temp_dir"
            return 0
        else
            log_error "Failed to create H2 backup archive"
            rm -rf "$temp_dir"
            return 1
        fi
    else
        log_error "Failed to copy H2 database files"
        rm -rf "$temp_dir"
        return 1
    fi
}

# ===========================================
# 백업 검증 함수
# ===========================================

verify_backup() {
    local backup_file="$1"
    
    if [[ "$VERIFY_BACKUP" != "true" ]]; then
        return 0
    fi
    
    log_info "Verifying backup file: $backup_file"
    
    # 파일 존재 확인
    if [[ ! -f "$backup_file" ]]; then
        log_error "Backup file does not exist: $backup_file"
        return 1
    fi
    
    # 파일 크기 확인 (최소 1KB)
    local file_size
    file_size="$(stat -f%z "$backup_file" 2>/dev/null || stat -c%s "$backup_file" 2>/dev/null || echo 0)"
    
    if [[ "$file_size" -lt 1024 ]]; then
        log_error "Backup file is too small (${file_size} bytes): $backup_file"
        return 1
    fi
    
    # 압축 파일 무결성 확인
    case "$backup_file" in
        *.gz)
            if ! gzip -t "$backup_file"; then
                log_error "Backup file is corrupted (gzip test failed): $backup_file"
                return 1
            fi
            ;;
        *.xz)
            if ! xz -t "$backup_file"; then
                log_error "Backup file is corrupted (xz test failed): $backup_file"
                return 1
            fi
            ;;
    esac
    
    log_info "Backup verification successful: $backup_file (${file_size} bytes)"
    return 0
}

# ===========================================
# 백업 정리 함수
# ===========================================

cleanup_old_backups() {
    log_info "Cleaning up backups older than $RETENTION_DAYS days..."
    
    local deleted_count=0
    local total_size=0
    
    while IFS= read -r -d '' backup_file; do
        local file_age_days
        file_age_days="$(( ($(date +%s) - $(stat -f%m "$backup_file" 2>/dev/null || stat -c%Y "$backup_file" 2>/dev/null || echo 0)) / 86400 ))"
        
        if [[ "$file_age_days" -gt "$RETENTION_DAYS" ]]; then
            local file_size
            file_size="$(stat -f%z "$backup_file" 2>/dev/null || stat -c%s "$backup_file" 2>/dev/null || echo 0)"
            
            if rm "$backup_file"; then
                log_info "Deleted old backup: $backup_file (${file_age_days} days old, ${file_size} bytes)"
                ((deleted_count++))
                ((total_size += file_size))
            else
                log_warn "Failed to delete old backup: $backup_file"
            fi
        fi
    done < <(find "$BACKUP_DIR" -name "*.sql*" -type f -print0)
    
    if [[ "$deleted_count" -gt 0 ]]; then
        log_info "Cleanup completed: $deleted_count files deleted, $(numfmt --to=iec $total_size) freed"
    else
        log_info "No old backups to clean up"
    fi
}

# ===========================================
# 알림 함수
# ===========================================

send_notification() {
    local status="$1"
    local message="$2"
    
    if [[ "$NOTIFICATION_ENABLED" != "true" ]]; then
        return 0
    fi
    
    local timestamp
    timestamp="$(date '+%Y-%m-%d %H:%M:%S')"
    
    # Slack 알림
    if [[ -n "$SLACK_WEBHOOK_URL" ]]; then
        local color="good"
        if [[ "$status" != "SUCCESS" ]]; then
            color="danger"
        fi
        
        local payload
        payload=$(cat <<EOF
{
    "attachments": [
        {
            "color": "$color",
            "title": "GPU Management DB Backup - $status",
            "text": "$message",
            "fields": [
                {
                    "title": "Server",
                    "value": "$(hostname)",
                    "short": true
                },
                {
                    "title": "Database",
                    "value": "$DB_TYPE ($DB_HOST:$DB_PORT)",
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
        
        if curl -X POST -H 'Content-type: application/json' \
               --data "$payload" \
               --silent \
               --max-time 10 \
               "$SLACK_WEBHOOK_URL" >/dev/null; then
            log_debug "Slack notification sent successfully"
        else
            log_warn "Failed to send Slack notification"
        fi
    fi
    
    # 이메일 알림
    if [[ -n "$EMAIL_RECIPIENTS" ]] && command -v mail >/dev/null; then
        local subject="GPU Management DB Backup - $status"
        
        local email_body
        email_body=$(cat <<EOF
GPU Management Database Backup Report

Status: $status
Message: $message
Server: $(hostname)
Database: $DB_TYPE ($DB_HOST:$DB_PORT)
Timestamp: $timestamp

Backup Details:
- Backup Type: $BACKUP_TYPE
- Compression: $COMPRESSION
- Retention: $RETENTION_DAYS days

Log file: $LOG_FILE
EOF
        )
        
        if echo "$email_body" | mail -s "$subject" $EMAIL_RECIPIENTS; then
            log_debug "Email notification sent successfully"
        else
            log_warn "Failed to send email notification"
        fi
    fi
}

# ===========================================
# 메인 백업 함수
# ===========================================

perform_backup() {
    local backup_file
    backup_file="$(get_backup_filename)"
    
    log_info "Starting $DB_TYPE backup (type: $BACKUP_TYPE)"
    log_info "Backup file: $backup_file"
    
    local start_time
    start_time="$(date +%s)"
    
    # 데이터베이스별 백업 실행
    case "$DB_TYPE" in
        postgresql)
            if ! backup_postgresql "$backup_file"; then
                return 1
            fi
            ;;
        oracle)
            if ! backup_oracle "$backup_file"; then
                return 1
            fi
            ;;
        h2)
            if ! backup_h2 "$backup_file"; then
                return 1
            fi
            ;;
        *)
            log_error "Unsupported database type: $DB_TYPE"
            return 1
            ;;
    esac
    
    local end_time
    end_time="$(date +%s)"
    local duration=$((end_time - start_time))
    
    # 백업 검증
    if ! verify_backup "$backup_file"; then
        return 1
    fi
    
    # 백업 정보 로깅
    local file_size
    file_size="$(stat -f%z "$backup_file" 2>/dev/null || stat -c%s "$backup_file" 2>/dev/null || echo 0)"
    
    log_info "Backup completed successfully:"
    log_info "  File: $backup_file"
    log_info "  Size: $(numfmt --to=iec $file_size)"
    log_info "  Duration: ${duration}s"
    
    # 성공 알림
    send_notification "SUCCESS" "Database backup completed successfully. Size: $(numfmt --to=iec $file_size), Duration: ${duration}s"
    
    return 0
}

# ===========================================
# 사용법 표시
# ===========================================

show_usage() {
    cat <<EOF
Usage: $SCRIPT_NAME [OPTIONS]

GPU Management Database Backup Script

OPTIONS:
    -h, --help              Show this help message
    -c, --config FILE       Configuration file path (default: $CONFIG_FILE)
    -t, --type TYPE         Backup type: full|incremental|differential (default: full)
    -d, --database TYPE     Database type: postgresql|oracle|h2 (default: postgresql)
    -o, --output DIR        Backup output directory (default: $BACKUP_DIR)
    -r, --retention DAYS    Backup retention in days (default: $RETENTION_DAYS)
    --compression TYPE      Compression type: gzip|xz|none (default: gzip)
    --parallel JOBS         Number of parallel jobs (default: $PARALLEL_JOBS)
    --verify                Verify backup after creation (default: enabled)
    --no-verify             Skip backup verification
    --cleanup-only          Only perform cleanup of old backups
    --dry-run               Show what would be done without executing
    --debug                 Enable debug logging
    -v, --verbose           Verbose output

EXAMPLES:
    $SCRIPT_NAME                                    # Full backup with default settings
    $SCRIPT_NAME --type incremental                 # Incremental backup
    $SCRIPT_NAME --database oracle --parallel 8    # Oracle backup with 8 parallel jobs
    $SCRIPT_NAME --cleanup-only                     # Only cleanup old backups
    $SCRIPT_NAME --dry-run                          # Show what would be done

CONFIGURATION:
    Configuration can be provided via:
    1. Command line options
    2. Configuration file ($CONFIG_FILE)
    3. Environment variables

    Example configuration file:
    DB_TYPE=postgresql
    DB_HOST=localhost
    DB_PORT=5432
    DB_NAME=gpu_management
    DB_USER=gpu_admin
    DB_PASSWORD=secretpassword
    BACKUP_DIR=/backup/gpu-management
    RETENTION_DAYS=30
    COMPRESSION=gzip
    NOTIFICATION_ENABLED=true
    SLACK_WEBHOOK_URL=https://hooks.slack.com/...
EOF
}

# ===========================================
# 메인 함수
# ===========================================

main() {
    local cleanup_only=false
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
            -t|--type)
                BACKUP_TYPE="$2"
                shift 2
                ;;
            -d|--database)
                DB_TYPE="$2"
                shift 2
                ;;
            -o|--output)
                BACKUP_DIR="$2"
                shift 2
                ;;
            -r|--retention)
                RETENTION_DAYS="$2"
                shift 2
                ;;
            --compression)
                COMPRESSION="$2"
                shift 2
                ;;
            --parallel)
                PARALLEL_JOBS="$2"
                shift 2
                ;;
            --verify)
                VERIFY_BACKUP=true
                shift
                ;;
            --no-verify)
                VERIFY_BACKUP=false
                shift
                ;;
            --cleanup-only)
                cleanup_only=true
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
    
    # 종료 시그널 핸들링
    trap 'log_error "Backup interrupted by signal"; send_notification "FAILED" "Backup was interrupted by signal"; exit 130' INT TERM
    
    log_info "Starting GPU Management database backup"
    log_info "Configuration: DB_TYPE=$DB_TYPE, BACKUP_TYPE=$BACKUP_TYPE, COMPRESSION=$COMPRESSION"
    
    # Dry run 모드
    if [[ "$dry_run" == "true" ]]; then
        log_info "DRY RUN MODE - No actual backup will be performed"
        log_info "Would backup: $DB_TYPE database ($DB_HOST:$DB_PORT/$DB_NAME)"
        log_info "Backup directory: $BACKUP_DIR"
        log_info "Retention: $RETENTION_DAYS days"
        log_info "Compression: $COMPRESSION"
        exit 0
    fi
    
    # 의존성 확인
    if ! check_dependencies; then
        send_notification "FAILED" "Missing required dependencies"
        exit 1
    fi
    
    # Cleanup only 모드
    if [[ "$cleanup_only" == "true" ]]; then
        log_info "Cleanup only mode - performing backup cleanup"
        cleanup_old_backups
        exit 0
    fi
    
    # 백업 디렉토리 생성
    if ! create_backup_directory; then
        send_notification "FAILED" "Failed to create backup directory"
        exit 1
    fi
    
    # 백업 실행
    if perform_backup; then
        log_info "Backup process completed successfully"
        
        # 오래된 백업 정리
        cleanup_old_backups
        
        exit 0
    else
        log_error "Backup process failed"
        send_notification "FAILED" "Database backup failed - check logs for details"
        exit 1
    fi
}

# 스크립트 실행
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi