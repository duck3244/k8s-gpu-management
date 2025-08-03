#!/bin/bash

# GPU 관리 시스템 오래된 메트릭 정리 스크립트
# 데이터베이스에서 오래된 GPU 메트릭 데이터를 정리하여 성능 최적화
# 작성자: GPU Management System
# 버전: 1.0.0

set -euo pipefail

# ===========================================
# 설정 변수
# ===========================================

# 스크립트 기본 설정
SCRIPT_NAME="$(basename "$0")"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_FILE="${LOG_FILE:-/var/log/gpu-management/cleanup-metrics.log}"
CONFIG_FILE="${CONFIG_FILE:-/etc/gpu-management/cleanup-metrics.conf}"

# 데이터베이스 연결 설정
DB_TYPE="${DB_TYPE:-postgresql}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-gpu_management}"
DB_USER="${DB_USER:-gpu_admin}"
DB_PASSWORD="${DB_PASSWORD:-}"
DB_SERVICE_NAME="${DB_SERVICE_NAME:-ORCL}"

# 정리 설정
DEFAULT_RETENTION_DAYS="${DEFAULT_RETENTION_DAYS:-30}"
BATCH_SIZE="${BATCH_SIZE:-10000}"
PARALLEL_WORKERS="${PARALLEL_WORKERS:-4}"
VACUUM_AFTER_DELETE="${VACUUM_AFTER_DELETE:-true}"
ANALYZE_AFTER_DELETE="${ANALYZE_AFTER_DELETE:-true}"

# 테이블별 보존 기간 설정 (일)
declare -A TABLE_RETENTION=(
    ["gpu_usage_metrics"]="30"
    ["resource_metrics"]="30"
    ["gpu_allocation_history"]="90"
    ["mig_usage_history"]="30"
    ["gpu_cost_history"]="180"
    ["gpu_alerts_history"]="90"
    ["gpu_benchmark_results"]="365"
    ["system_events"]="60"
)

# 알림 설정
NOTIFICATION_ENABLED="${NOTIFICATION_ENABLED:-false}"
SLACK_WEBHOOK_URL="${SLACK_WEBHOOK_URL:-}"
EMAIL_RECIPIENTS="${EMAIL_RECIPIENTS:-}"

# 성능 설정
MAX_EXECUTION_TIME="${MAX_EXECUTION_TIME:-3600}"  # 1시간
LOCK_TIMEOUT="${LOCK_TIMEOUT:-30}"  # 30초
CHECKPOINT_INTERVAL="${CHECKPOINT_INTERVAL:-1000}"  # 1000 배치마다

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
        echo "GPU Management Metrics Cleanup"
        echo "Started: $(date '+%Y-%m-%d %H:%M:%S')"
        echo "Script: $SCRIPT_NAME"
        echo "PID: $$"
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
# 데이터베이스 연결 함수
# ===========================================

connect_database() {
    log_info "Connecting to $DB_TYPE database ($DB_HOST:$DB_PORT)"
    
    case "$DB_TYPE" in
        postgresql)
            export PGPASSWORD="$DB_PASSWORD"
            if psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "SELECT 1;" >/dev/null 2>&1; then
                log_info "PostgreSQL connection successful"
                return 0
            else
                log_error "Failed to connect to PostgreSQL"
                return 1
            fi
            ;;
        oracle)
            if sqlplus -S "$DB_USER/$DB_PASSWORD@$DB_HOST:$DB_PORT/$DB_SERVICE_NAME" <<< "SELECT 1 FROM DUAL;" >/dev/null 2>&1; then
                log_info "Oracle connection successful"
                return 0
            else
                log_error "Failed to connect to Oracle"
                return 1
            fi
            ;;
        h2)
            # H2는 파일 기반이므로 파일 존재 확인
            local h2_db_path="${H2_DB_PATH:-/var/lib/gpu-management/h2}"
            if [[ -d "$h2_db_path" ]]; then
                log_info "H2 database directory found"
                return 0
            else
                log_error "H2 database directory not found: $h2_db_path"
                return 1
            fi
            ;;
        *)
            log_error "Unsupported database type: $DB_TYPE"
            return 1
            ;;
    esac
}

# ===========================================
# SQL 실행 함수
# ===========================================

execute_sql() {
    local sql="$1"
    local description="${2:-SQL execution}"
    
    log_debug "Executing SQL: $description"
    log_debug "SQL: $sql"
    
    case "$DB_TYPE" in
        postgresql)
            psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
                 -c "$sql" 2>/dev/null
            ;;
        oracle)
            sqlplus -S "$DB_USER/$DB_PASSWORD@$DB_HOST:$DB_PORT/$DB_SERVICE_NAME" <<< "$sql"
            ;;
        h2)
            # H2는 Java 기반 실행이 필요 (실제 구현시 H2 도구 사용)
            log_warn "H2 SQL execution not implemented in this script"
            return 1
            ;;
        *)
            log_error "Unsupported database type for SQL execution: $DB_TYPE"
            return 1
            ;;
    esac
}

execute_sql_with_result() {
    local sql="$1"
    local description="${2:-SQL query}"
    
    log_debug "Executing SQL query: $description"
    
    case "$DB_TYPE" in
        postgresql)
            psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
                 -t -A -c "$sql" 2>/dev/null
            ;;
        oracle)
            sqlplus -S "$DB_USER/$DB_PASSWORD@$DB_HOST:$DB_PORT/$DB_SERVICE_NAME" <<< "SET PAGESIZE 0; SET FEEDBACK OFF; $sql"
            ;;
        *)
            log_error "Unsupported database type for query: $DB_TYPE"
            return 1
            ;;
    esac
}

# ===========================================
# 테이블 분석 함수
# ===========================================

analyze_table_sizes() {
    log_info "Analyzing table sizes before cleanup"
    
    local tables=(
        "gpu_usage_metrics"
        "resource_metrics" 
        "gpu_allocations"
        "mig_instances"
        "gpu_devices"
    )
    
    for table in "${tables[@]}"; do
        local row_count
        local table_size
        
        case "$DB_TYPE" in
            postgresql)
                row_count=$(execute_sql_with_result "SELECT COUNT(*) FROM $table;" "Count rows in $table")
                table_size=$(execute_sql_with_result "SELECT pg_size_pretty(pg_total_relation_size('$table'));" "Get size of $table")
                ;;
            oracle)
                row_count=$(execute_sql_with_result "SELECT COUNT(*) FROM $table;" "Count rows in $table")
                table_size=$(execute_sql_with_result "SELECT ROUND(SUM(bytes)/1024/1024,2)||' MB' FROM user_segments WHERE segment_name = UPPER('$table');" "Get size of $table")
                ;;
        esac
        
        if [[ -n "$row_count" && -n "$table_size" ]]; then
            log_info "Table $table: $row_count rows, $table_size"
        else
            log_warn "Could not analyze table: $table"
        fi
    done
}

get_table_row_count() {
    local table="$1"
    local cutoff_date="$2"
    
    local sql
    case "$DB_TYPE" in
        postgresql)
            sql="SELECT COUNT(*) FROM $table WHERE created_at < '$cutoff_date' OR timestamp < '$cutoff_date';"
            ;;
        oracle)
            sql="SELECT COUNT(*) FROM $table WHERE created_date < TO_DATE('$cutoff_date', 'YYYY-MM-DD') OR timestamp < TO_DATE('$cutoff_date', 'YYYY-MM-DD');"
            ;;
    esac
    
    execute_sql_with_result "$sql" "Count old rows in $table"
}

# ===========================================
# 배치 삭제 함수
# ===========================================

delete_old_metrics_batch() {
    local table="$1"
    local retention_days="$2"
    local batch_size="$3"
    
    log_info "Starting batch deletion for table: $table (retention: ${retention_days} days)"
    
    local cutoff_date
    cutoff_date=$(date -d "${retention_days} days ago" '+%Y-%m-%d')
    
    # 삭제할 레코드 수 확인
    local total_old_records
    total_old_records=$(get_table_row_count "$table" "$cutoff_date")
    
    if [[ -z "$total_old_records" ]] || [[ "$total_old_records" -eq 0 ]]; then
        log_info "No old records to delete in table: $table"
        return 0
    fi
    
    log_info "Found $total_old_records old records in $table to delete"
    
    local deleted_total=0
    local batch_count=0
    local start_time
    start_time=$(date +%s)
    
    # 삭제 SQL 준비
    local delete_sql
    case "$DB_TYPE" in
        postgresql)
            delete_sql="DELETE FROM $table WHERE (created_at < '$cutoff_date' OR timestamp < '$cutoff_date') AND ctid IN (SELECT ctid FROM $table WHERE (created_at < '$cutoff_date' OR timestamp < '$cutoff_date') LIMIT $batch_size);"
            ;;
        oracle)
            delete_sql="DELETE FROM $table WHERE (created_date < TO_DATE('$cutoff_date', 'YYYY-MM-DD') OR timestamp < TO_DATE('$cutoff_date', 'YYYY-MM-DD')) AND ROWNUM <= $batch_size;"
            ;;
    esac
    
    # 배치 삭제 실행
    while true; do
        local batch_start_time
        batch_start_time=$(date +%s)
        
        log_debug "Executing batch $((batch_count + 1)) for table $table"
        
        local deleted_count
        case "$DB_TYPE" in
            postgresql)
                deleted_count=$(execute_sql_with_result "WITH deleted AS ($delete_sql RETURNING 1) SELECT COUNT(*) FROM deleted;" "Batch delete from $table")
                ;;
            oracle)
                execute_sql "$delete_sql" "Batch delete from $table"
                deleted_count=$(execute_sql_with_result "SELECT SQL%ROWCOUNT FROM DUAL;" "Get deleted row count")
                ;;
        esac
        
        if [[ -z "$deleted_count" ]] || [[ "$deleted_count" -eq 0 ]]; then
            log_info "No more records to delete from $table"
            break
        fi
        
        deleted_total=$((deleted_total + deleted_count))
        batch_count=$((batch_count + 1))
        
        local batch_duration=$(($(date +%s) - batch_start_time))
        log_info "Batch $batch_count completed: deleted $deleted_count records from $table (${batch_duration}s)"
        
        # 진행률 표시
        local progress=$((deleted_total * 100 / total_old_records))
        log_info "Progress: $deleted_total/$total_old_records ($progress%) deleted from $table"
        
        # 체크포인트
        if [[ $((batch_count % CHECKPOINT_INTERVAL)) -eq 0 ]]; then
            log_info "Checkpoint: $batch_count batches completed, committing transaction"
            execute_sql "COMMIT;" "Commit transaction"
        fi
        
        # 실행 시간 제한 확인
        local total_duration=$(($(date +%s) - start_time))
        if [[ $total_duration -gt $MAX_EXECUTION_TIME ]]; then
            log_warn "Maximum execution time exceeded for $table, stopping deletion"
            break
        fi
        
        # 잠시 대기 (부하 분산)
        sleep 0.1
    done
    
    local total_duration=$(($(date +%s) - start_time))
    log_info "Batch deletion completed for $table: $deleted_total records deleted in $batch_count batches (${total_duration}s)"
    
    return 0
}

# ===========================================
# 인덱스 최적화 함수
# ===========================================

optimize_indexes() {
    local table="$1"
    
    log_info "Optimizing indexes for table: $table"
    
    case "$DB_TYPE" in
        postgresql)
            # VACUUM과 ANALYZE 실행
            if [[ "$VACUUM_AFTER_DELETE" == "true" ]]; then
                log_info "Running VACUUM on $table"
                execute_sql "VACUUM $table;" "VACUUM $table"
            fi
            
            if [[ "$ANALYZE_AFTER_DELETE" == "true" ]]; then
                log_info "Running ANALYZE on $table"
                execute_sql "ANALYZE $table;" "ANALYZE $table"
            fi
            
            # 인덱스 통계 업데이트
            execute_sql "REINDEX TABLE $table;" "REINDEX $table"
            ;;
        oracle)
            # Oracle 통계 수집
            if [[ "$ANALYZE_AFTER_DELETE" == "true" ]]; then
                log_info "Gathering table statistics for $table"
                execute_sql "BEGIN DBMS_STATS.GATHER_TABLE_STATS(USER, UPPER('$table')); END;/" "Gather stats for $table"
            fi
            
            # 인덱스 재구성
            local indexes
            indexes=$(execute_sql_with_result "SELECT index_name FROM user_indexes WHERE table_name = UPPER('$table');" "Get indexes for $table")
            
            while IFS= read -r index_name; do
                if [[ -n "$index_name" ]]; then
                    log_debug "Rebuilding index: $index_name"
                    execute_sql "ALTER INDEX $index_name REBUILD;" "Rebuild index $index_name"
                fi
            done <<< "$indexes"
            ;;
    esac
}

# ===========================================
# 테이블별 정리 함수
# ===========================================

cleanup_gpu_usage_metrics() {
    local table="gpu_usage_metrics"
    local retention_days="${TABLE_RETENTION[$table]}"
    
    log_info "Cleaning up GPU usage metrics (retention: ${retention_days} days)"
    
    # 특별 처리: 높은 사용률 메트릭은 더 오래 보관
    local cutoff_date
    cutoff_date=$(date -d "${retention_days} days ago" '+%Y-%m-%d')
    
    local high_util_cutoff_date
    high_util_cutoff_date=$(date -d "$((retention_days * 2)) days ago" '+%Y-%m-%d')
    
    # 일반 메트릭 삭제
    case "$DB_TYPE" in
        postgresql)
            local delete_sql="DELETE FROM $table WHERE timestamp < '$cutoff_date' AND (gpu_utilization_pct < 80 OR gpu_utilization_pct IS NULL)"
            ;;
        oracle)
            local delete_sql="DELETE FROM $table WHERE timestamp < TO_DATE('$cutoff_date', 'YYYY-MM-DD') AND (gpu_utilization_pct < 80 OR gpu_utilization_pct IS NULL)"
            ;;
    esac
    
    delete_old_metrics_batch "$table" "$retention_days" "$BATCH_SIZE"
    
    # 높은 사용률 메트릭 삭제 (더 오래된 것만)
    log_info "Cleaning up high utilization metrics (retention: $((retention_days * 2)) days)"
    case "$DB_TYPE" in
        postgresql)
            execute_sql "DELETE FROM $table WHERE timestamp < '$high_util_cutoff_date' AND gpu_utilization_pct >= 80;" "Delete old high utilization metrics"
            ;;
        oracle)
            execute_sql "DELETE FROM $table WHERE timestamp < TO_DATE('$high_util_cutoff_date', 'YYYY-MM-DD') AND gpu_utilization_pct >= 80;" "Delete old high utilization metrics"
            ;;
    esac
    
    optimize_indexes "$table"
}

cleanup_resource_metrics() {
    local table="resource_metrics"
    local retention_days="${TABLE_RETENTION[$table]}"
    
    delete_old_metrics_batch "$table" "$retention_days" "$BATCH_SIZE"
    optimize_indexes "$table"
}

cleanup_allocation_history() {
    local table="gpu_allocations"
    local retention_days="${TABLE_RETENTION[gpu_allocation_history]}"
    
    log_info "Cleaning up old GPU allocations (retention: ${retention_days} days)"
    
    # 완료된 할당만 삭제
    local cutoff_date
    cutoff_date=$(date -d "${retention_days} days ago" '+%Y-%m-%d')
    
    case "$DB_TYPE" in
        postgresql)
            execute_sql "DELETE FROM $table WHERE release_time < '$cutoff_date' AND status IN ('RELEASED', 'EXPIRED', 'FAILED');" "Delete old completed allocations"
            ;;
        oracle)
            execute_sql "DELETE FROM $table WHERE release_time < TO_DATE('$cutoff_date', 'YYYY-MM-DD') AND status IN ('RELEASED', 'EXPIRED', 'FAILED');" "Delete old completed allocations"
            ;;
    esac
    
    optimize_indexes "$table"
}

cleanup_system_events() {
    local table="system_events"
    local retention_days="${TABLE_RETENTION[$table]}"
    
    if table_exists "$table"; then
        delete_old_metrics_batch "$table" "$retention_days" "$BATCH_SIZE"
        optimize_indexes "$table"
    else
        log_debug "Table $table does not exist, skipping"
    fi
}

cleanup_alert_history() {
    local table="gpu_alerts_history"
    local retention_days="${TABLE_RETENTION[$table]}"
    
    if table_exists "$table"; then
        delete_old_metrics_batch "$table" "$retention_days" "$BATCH_SIZE"
        optimize_indexes "$table"
    else
        log_debug "Table $table does not exist, skipping"
    fi
}

# ===========================================
# 유틸리티 함수
# ===========================================

table_exists() {
    local table="$1"
    
    case "$DB_TYPE" in
        postgresql)
            local count
            count=$(execute_sql_with_result "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = '$table';" "Check if table exists")
            [[ "$count" -gt 0 ]]
            ;;
        oracle)
            local count
            count=$(execute_sql_with_result "SELECT COUNT(*) FROM user_tables WHERE table_name = UPPER('$table');" "Check if table exists")
            [[ "$count" -gt 0 ]]
            ;;
    esac
}

get_database_size() {
    case "$DB_TYPE" in
        postgresql)
            execute_sql_with_result "SELECT pg_size_pretty(pg_database_size('$DB_NAME'));" "Get database size"
            ;;
        oracle)
            execute_sql_with_result "SELECT ROUND(SUM(bytes)/1024/1024/1024,2)||' GB' FROM dba_data_files;" "Get database size"
            ;;
    esac
}

# ===========================================
# 병렬 처리 함수
# ===========================================

cleanup_tables_parallel() {
    log_info "Starting parallel cleanup with $PARALLEL_WORKERS workers"
    
    local tables=(
        "gpu_usage_metrics"
        "resource_metrics"
        "gpu_allocations"
        "system_events"
        "gpu_alerts_history"
    )
    
    local pids=()
    
    for table in "${tables[@]}"; do
        # 워커 수 제한
        while [[ ${#pids[@]} -ge $PARALLEL_WORKERS ]]; do
            for i in "${!pids[@]}"; do
                if ! kill -0 "${pids[i]}" 2>/dev/null; then
                    wait "${pids[i]}"
                    unset "pids[i]"
                fi
            done
            pids=("${pids[@]}")  # 배열 재정렬
            sleep 1
        done
        
        # 백그라운드에서 테이블 정리 실행
        (
            case "$table" in
                "gpu_usage_metrics")
                    cleanup_gpu_usage_metrics
                    ;;
                "resource_metrics")
                    cleanup_resource_metrics
                    ;;
                "gpu_allocations")
                    cleanup_allocation_history
                    ;;
                "system_events")
                    cleanup_system_events
                    ;;
                "gpu_alerts_history")
                    cleanup_alert_history
                    ;;
            esac
        ) &
        
        pids+=($!)
        log_info "Started cleanup worker for $table (PID: $!)"
    done
    
    # 모든 워커 완료 대기
    for pid in "${pids[@]}"; do
        wait "$pid"
        log_info "Cleanup worker completed (PID: $pid)"
    done
    
    log_info "Parallel cleanup completed"
}

# ===========================================
# 메인 정리 함수
# ===========================================

perform_cleanup() {
    log_info "Starting GPU metrics cleanup process"
    
    local start_time
    start_time=$(date +%s)
    
    # 정리 전 분석
    local db_size_before
    db_size_before=$(get_database_size)
    log_info "Database size before cleanup: $db_size_before"
    
    analyze_table_sizes
    
    # 정리 실행
    if [[ "$PARALLEL_WORKERS" -gt 1 ]]; then
        cleanup_tables_parallel
    else
        cleanup_gpu_usage_metrics
        cleanup_resource_metrics
        cleanup_allocation_history
        cleanup_system_events
        cleanup_alert_history
    fi
    
    # 전체 데이터베이스 최적화
    log_info "Performing database-wide optimization"
    case "$DB_TYPE" in
        postgresql)
            if [[ "$VACUUM_AFTER_DELETE" == "true" ]]; then
                execute_sql "VACUUM;" "Full database VACUUM"
            fi
            if [[ "$ANALYZE_AFTER_DELETE" == "true" ]]; then
                execute_sql "ANALYZE;" "Full database ANALYZE"
            fi
            ;;
        oracle)
            if [[ "$ANALYZE_AFTER_DELETE" == "true" ]]; then
                execute_sql "BEGIN DBMS_STATS.GATHER_DATABASE_STATS; END;/" "Gather database statistics"
            fi
            ;;
    esac
    
    # 정리 후 분석
    local db_size_after
    db_size_after=$(get_database_size)
    log_info "Database size after cleanup: $db_size_after"
    
    local total_time=$(($(date +%s) - start_time))
    log_info "Cleanup process completed in ${total_time} seconds"
    
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
            "title": "GPU Management Metrics Cleanup - $status",
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
        
        curl -X POST -H 'Content-type: application/json' \
             --data "$payload" \
             --silent \
             --max-time 10 \
             "$SLACK_WEBHOOK_URL" >/dev/null || true
    fi
    
    # 이메일 알림
    if [[ -n "$EMAIL_RECIPIENTS" ]] && command -v mail >/dev/null; then
        local subject="GPU Management Metrics Cleanup - $status"
        
        local email_body
        email_body=$(cat <<EOF
GPU Management Metrics Cleanup Report

Status: $status
Message: $message
Server: $(hostname)
Database: $DB_TYPE ($DB_HOST:$DB_PORT)
Timestamp: $timestamp

$details

Configuration:
- Default Retention: $DEFAULT_RETENTION_DAYS days
- Batch Size: $BATCH_SIZE
- Parallel Workers: $PARALLEL_WORKERS

Log file: $LOG_FILE
EOF
        )
        
        echo "$email_body" | mail -s "$subject" $EMAIL_RECIPIENTS || true
    fi
}

# ===========================================
# 사용법 표시
# ===========================================

show_usage() {
    cat <<EOF
Usage: $SCRIPT_NAME [OPTIONS]

GPU Management Metrics Cleanup Script

OPTIONS:
    -h, --help              Show this help message
    -c, --config FILE       Configuration file path (default: $CONFIG_FILE)
    -d, --database TYPE     Database type: postgresql|oracle|h2 (default: postgresql)
    -r, --retention DAYS    Default retention period in days (default: $DEFAULT_RETENTION_DAYS)
    -b, --batch-size SIZE   Batch size for deletions (default: $BATCH_SIZE)
    -p, --parallel WORKERS  Number of parallel workers (default: $PARALLEL_WORKERS)
    --table TABLE           Clean up specific table only
    --dry-run               Show what would be deleted without executing
    --analyze-only          Only analyze table sizes
    --no-vacuum             Skip VACUUM operation
    --no-analyze            Skip ANALYZE operation
    --debug                 Enable debug logging
    -v, --verbose           Verbose output

EXAMPLES:
    $SCRIPT_NAME                                    # Clean up with default settings
    $SCRIPT_NAME --retention 7                     # Clean up data older than 7 days
    $SCRIPT_NAME --table gpu_usage_metrics         # Clean up specific table only
    $SCRIPT_NAME --parallel 8 --batch-size 50000   # Use 8 workers with larger batches
    $SCRIPT_NAME --dry-run                          # Show what would be deleted
    $SCRIPT_NAME --analyze-only                     # Only show table sizes

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
    DEFAULT_RETENTION_DAYS=30
    BATCH_SIZE=10000
    PARALLEL_WORKERS=4
    NOTIFICATION_ENABLED=true
    SLACK_WEBHOOK_URL=https://hooks.slack.com/...
EOF
}

# ===========================================
# 메인 함수
# ===========================================

main() {
    local specific_table=""
    local dry_run=false
    local analyze_only=false
    
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
            -d|--database)
                DB_TYPE="$2"
                shift 2
                ;;
            -r|--retention)
                DEFAULT_RETENTION_DAYS="$2"
                shift 2
                ;;
            -b|--batch-size)
                BATCH_SIZE="$2"
                shift 2
                ;;
            -p|--parallel)
                PARALLEL_WORKERS="$2"
                shift 2
                ;;
            --table)
                specific_table="$2"
                shift 2
                ;;
            --dry-run)
                dry_run=true
                shift
                ;;
            --analyze-only)
                analyze_only=true
                shift
                ;;
            --no-vacuum)
                VACUUM_AFTER_DELETE=false
                shift
                ;;
            --no-analyze)
                ANALYZE_AFTER_DELETE=false
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
    trap 'log_error "Cleanup interrupted by signal"; send_notification "FAILED" "Metrics cleanup was interrupted by signal"; exit 130' INT TERM
    
    log_info "Starting GPU Management metrics cleanup"
    log_info "Configuration: DB_TYPE=$DB_TYPE, RETENTION=$DEFAULT_RETENTION_DAYS days, BATCH_SIZE=$BATCH_SIZE"
    
    # 데이터베이스 연결 확인
    if ! connect_database; then
        send_notification "FAILED" "Failed to connect to database"
        exit 1
    fi
    
    # Analyze only 모드
    if [[ "$analyze_only" == "true" ]]; then
        log_info "Analyze only mode - showing table sizes"
        analyze_table_sizes
        exit 0
    fi
    
    # Dry run 모드
    if [[ "$dry_run" == "true" ]]; then
        log_info "DRY RUN MODE - No actual cleanup will be performed"
        
        for table in "${!TABLE_RETENTION[@]}"; do
            local retention_days="${TABLE_RETENTION[$table]}"
            local cutoff_date
            cutoff_date=$(date -d "${retention_days} days ago" '+%Y-%m-%d')
            
            local old_count
            old_count=$(get_table_row_count "$table" "$cutoff_date")
            
            if [[ -n "$old_count" ]] && [[ "$old_count" -gt 0 ]]; then
                log_info "Would delete $old_count records from $table (older than $cutoff_date)"
            else
                log_info "No old records to delete from $table"
            fi
        done
        exit 0
    fi
    
    # 특정 테이블만 정리
    if [[ -n "$specific_table" ]]; then
        log_info "Cleaning up specific table: $specific_table"
        
        case "$specific_table" in
            "gpu_usage_metrics")
                cleanup_gpu_usage_metrics
                ;;
            "resource_metrics")
                cleanup_resource_metrics
                ;;
            "gpu_allocations")
                cleanup_allocation_history
                ;;
            "system_events")
                cleanup_system_events
                ;;
            "gpu_alerts_history")
                cleanup_alert_history
                ;;
            *)
                log_error "Unknown table: $specific_table"
                exit 1
                ;;
        esac
        
        send_notification "SUCCESS" "Metrics cleanup completed for table: $specific_table"
        exit 0
    fi
    
    # 전체 정리 실행
    if perform_cleanup; then
        log_info "Metrics cleanup completed successfully"
        send_notification "SUCCESS" "GPU metrics cleanup completed successfully"
        exit 0
    else
        log_error "Metrics cleanup completed with errors"
        send_notification "FAILED" "GPU metrics cleanup completed with errors - check logs for details"
        exit 1
    fi
}

# 스크립트 실행
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi