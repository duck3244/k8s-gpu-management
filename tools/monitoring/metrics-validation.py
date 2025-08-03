#!/usr/bin/env python3
"""
GPU 메트릭 검증 도구
GPU 메트릭 데이터의 무결성, 정확성, 일관성을 검증하는 스크립트
"""

import sys
import json
import logging
import argparse
import requests
import psycopg2
import cx_Oracle
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Tuple
import numpy as np
import pandas as pd

# 로깅 설정
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('/var/log/gpu-management/metrics-validation.log'),
        logging.StreamHandler(sys.stdout)
    ]
)
logger = logging.getLogger(__name__)

class MetricsValidator:
    """GPU 메트릭 검증기 클래스"""
    
    def __init__(self, config: Dict):
        self.config = config
        self.api_base_url = config.get('api_base_url', 'http://localhost:8080/k8s-monitor/api/v1')
        self.db_config = config.get('database', {})
        self.validation_rules = config.get('validation_rules', {})
        self.connection = None
        
    def connect_database(self) -> bool:
        """데이터베이스 연결"""
        try:
            db_type = self.db_config.get('type', 'postgresql')
            
            if db_type == 'postgresql':
                self.connection = psycopg2.connect(
                    host=self.db_config['host'],
                    port=self.db_config['port'],
                    database=self.db_config['database'],
                    user=self.db_config['username'],
                    password=self.db_config['password']
                )
            elif db_type == 'oracle':
                dsn = cx_Oracle.makedsn(
                    self.db_config['host'],
                    self.db_config['port'],
                    service_name=self.db_config['service_name']
                )
                self.connection = cx_Oracle.connect(
                    user=self.db_config['username'],
                    password=self.db_config['password'],
                    dsn=dsn
                )
            
            logger.info(f"Successfully connected to {db_type} database")
            return True
            
        except Exception as e:
            logger.error(f"Failed to connect to database: {e}")
            return False
    
    def validate_api_endpoints(self) -> Dict[str, bool]:
        """API 엔드포인트 검증"""
        logger.info("Validating API endpoints...")
        results = {}
        
        endpoints = [
            '/gpu/overview',
            '/gpu/devices',
            '/gpu/metrics/usage-statistics',
            '/gpu/allocations',
            '/gpu/mig/available',
            '/gpu/cost/analysis'
        ]
        
        for endpoint in endpoints:
            try:
                url = f"{self.api_base_url}{endpoint}"
                response = requests.get(url, timeout=10)
                results[endpoint] = response.status_code == 200
                
                if response.status_code == 200:
                    logger.info(f"✓ {endpoint} - OK")
                else:
                    logger.warning(f"✗ {endpoint} - Status: {response.status_code}")
                    
            except Exception as e:
                logger.error(f"✗ {endpoint} - Error: {e}")
                results[endpoint] = False
        
        return results
    
    def validate_gpu_metrics_data(self) -> Dict[str, any]:
        """GPU 메트릭 데이터 검증"""
        logger.info("Validating GPU metrics data...")
        validation_results = {
            'data_integrity': {},
            'data_accuracy': {},
            'data_consistency': {},
            'anomalies': []
        }
        
        try:
            # 1. 데이터 무결성 검증
            validation_results['data_integrity'] = self._validate_data_integrity()
            
            # 2. 데이터 정확성 검증
            validation_results['data_accuracy'] = self._validate_data_accuracy()
            
            # 3. 데이터 일관성 검증
            validation_results['data_consistency'] = self._validate_data_consistency()
            
            # 4. 이상치 탐지
            validation_results['anomalies'] = self._detect_anomalies()
            
        except Exception as e:
            logger.error(f"Error during metrics validation: {e}")
            validation_results['error'] = str(e)
        
        return validation_results
    
    def _validate_data_integrity(self) -> Dict[str, any]:
        """데이터 무결성 검증"""
        logger.info("Checking data integrity...")
        results = {}
        
        cursor = self.connection.cursor()
        
        try:
            # 1. NULL 값 검증
            null_check_queries = [
                ("gpu_devices", "SELECT COUNT(*) FROM gpu_devices WHERE device_id IS NULL OR gpu_uuid IS NULL"),
                ("gpu_usage_metrics", "SELECT COUNT(*) FROM gpu_usage_metrics WHERE device_id IS NULL OR timestamp IS NULL"),
                ("gpu_allocations", "SELECT COUNT(*) FROM gpu_allocations WHERE allocation_id IS NULL")
            ]
            
            for table, query in null_check_queries:
                cursor.execute(query)
                null_count = cursor.fetchone()[0]
                results[f"{table}_null_count"] = null_count
                
                if null_count > 0:
                    logger.warning(f"Found {null_count} NULL values in {table}")
                else:
                    logger.info(f"✓ No NULL values in {table}")
            
            # 2. 중복 데이터 검증
            duplicate_check_queries = [
                ("gpu_devices", "SELECT gpu_uuid, COUNT(*) FROM gpu_devices GROUP BY gpu_uuid HAVING COUNT(*) > 1"),
                ("gpu_allocations", "SELECT allocation_id, COUNT(*) FROM gpu_allocations GROUP BY allocation_id HAVING COUNT(*) > 1")
            ]
            
            for table, query in duplicate_check_queries:
                cursor.execute(query)
                duplicates = cursor.fetchall()
                results[f"{table}_duplicates"] = len(duplicates)
                
                if duplicates:
                    logger.warning(f"Found {len(duplicates)} duplicate records in {table}")
                    for dup in duplicates[:5]:  # 최대 5개만 로깅
                        logger.warning(f"Duplicate in {table}: {dup}")
                else:
                    logger.info(f"✓ No duplicates in {table}")
            
            # 3. 외래키 무결성 검증
            fk_check_queries = [
                ("gpu_usage_metrics", "SELECT COUNT(*) FROM gpu_usage_metrics gum LEFT JOIN gpu_devices gd ON gum.device_id = gd.device_id WHERE gd.device_id IS NULL"),
                ("gpu_allocations", "SELECT COUNT(*) FROM gpu_allocations ga LEFT JOIN gpu_devices gd ON ga.allocated_resource = gd.device_id WHERE ga.resource_type = 'FULL_GPU' AND gd.device_id IS NULL")
            ]
            
            for table, query in fk_check_queries:
                cursor.execute(query)
                orphan_count = cursor.fetchone()[0]
                results[f"{table}_orphaned_records"] = orphan_count
                
                if orphan_count > 0:
                    logger.warning(f"Found {orphan_count} orphaned records in {table}")
                else:
                    logger.info(f"✓ No orphaned records in {table}")
            
        except Exception as e:
            logger.error(f"Error in data integrity validation: {e}")
            results['error'] = str(e)
        finally:
            cursor.close()
        
        return results
    
    def _validate_data_accuracy(self) -> Dict[str, any]:
        """데이터 정확성 검증"""
        logger.info("Checking data accuracy...")
        results = {}
        
        cursor = self.connection.cursor()
        
        try:
            # 1. 범위 검증
            range_checks = [
                ("gpu_utilization", "SELECT COUNT(*) FROM gpu_usage_metrics WHERE gpu_utilization_pct < 0 OR gpu_utilization_pct > 100"),
                ("memory_utilization", "SELECT COUNT(*) FROM gpu_usage_metrics WHERE memory_utilization_pct < 0 OR memory_utilization_pct > 100"),
                ("temperature", "SELECT COUNT(*) FROM gpu_usage_metrics WHERE temperature_c < 0 OR temperature_c > 120"),
                ("power_draw", "SELECT COUNT(*) FROM gpu_usage_metrics WHERE power_draw_w < 0 OR power_draw_w > 1000")
            ]
            
            for check_name, query in range_checks:
                cursor.execute(query)
                invalid_count = cursor.fetchone()[0]
                results[f"{check_name}_invalid_range"] = invalid_count
                
                if invalid_count > 0:
                    logger.warning(f"Found {invalid_count} out-of-range values for {check_name}")
                else:
                    logger.info(f"✓ All {check_name} values are in valid range")
            
            # 2. 논리적 일관성 검증
            logical_checks = [
                ("memory_usage", "SELECT COUNT(*) FROM gpu_usage_metrics WHERE memory_used_mb > memory_total_mb"),
                ("allocation_memory", "SELECT COUNT(*) FROM gpu_allocations ga JOIN gpu_devices gd ON ga.allocated_resource = gd.device_id JOIN gpu_models gm ON gd.model_id = gm.model_id WHERE ga.allocated_memory_gb > gm.memory_gb")
            ]
            
            for check_name, query in logical_checks:
                cursor.execute(query)
                inconsistent_count = cursor.fetchone()[0]
                results[f"{check_name}_logical_inconsistency"] = inconsistent_count
                
                if inconsistent_count > 0:
                    logger.warning(f"Found {inconsistent_count} logical inconsistencies in {check_name}")
                else:
                    logger.info(f"✓ No logical inconsistencies in {check_name}")
            
            # 3. 시간 검증
            time_checks = [
                ("future_timestamps", "SELECT COUNT(*) FROM gpu_usage_metrics WHERE timestamp > CURRENT_TIMESTAMP"),
                ("old_timestamps", "SELECT COUNT(*) FROM gpu_usage_metrics WHERE timestamp < CURRENT_TIMESTAMP - INTERVAL '365 days'")
            ]
            
            for check_name, query in time_checks:
                cursor.execute(query)
                invalid_count = cursor.fetchone()[0]
                results[f"{check_name}"] = invalid_count
                
                if invalid_count > 0:
                    logger.warning(f"Found {invalid_count} invalid timestamps for {check_name}")
                else:
                    logger.info(f"✓ All timestamps are valid for {check_name}")
            
        except Exception as e:
            logger.error(f"Error in data accuracy validation: {e}")
            results['error'] = str(e)
        finally:
            cursor.close()
        
        return results
    
    def _validate_data_consistency(self) -> Dict[str, any]:
        """데이터 일관성 검증"""
        logger.info("Checking data consistency...")
        results = {}
        
        cursor = self.connection.cursor()
        
        try:
            # 1. 할당 상태 일관성
            cursor.execute("""
                SELECT COUNT(*) FROM gpu_allocations ga 
                WHERE ga.status = 'ALLOCATED' 
                AND ga.resource_type = 'FULL_GPU'
                AND ga.allocated_resource NOT IN (
                    SELECT device_id FROM gpu_devices WHERE device_status = 'ACTIVE'
                )
            """)
            allocation_inconsistency = cursor.fetchone()[0]
            results['allocation_device_inconsistency'] = allocation_inconsistency
            
            if allocation_inconsistency > 0:
                logger.warning(f"Found {allocation_inconsistency} allocation-device status inconsistencies")
            else:
                logger.info("✓ Allocation-device status consistency verified")
            
            # 2. MIG 인스턴스 일관성
            cursor.execute("""
                SELECT COUNT(*) FROM mig_instances mi 
                WHERE mi.allocated = 'Y' 
                AND mi.mig_id NOT IN (
                    SELECT DISTINCT allocated_resource FROM gpu_allocations 
                    WHERE status = 'ALLOCATED' AND resource_type = 'MIG_INSTANCE'
                )
            """)
            mig_inconsistency = cursor.fetchone()[0]
            results['mig_allocation_inconsistency'] = mig_inconsistency
            
            if mig_inconsistency > 0:
                logger.warning(f"Found {mig_inconsistency} MIG allocation inconsistencies")
            else:
                logger.info("✓ MIG allocation consistency verified")
            
            # 3. 메트릭 시계열 일관성
            cursor.execute("""
                SELECT device_id, COUNT(*) as gap_count
                FROM (
                    SELECT device_id, timestamp,
                           LAG(timestamp) OVER (PARTITION BY device_id ORDER BY timestamp) as prev_timestamp
                    FROM gpu_usage_metrics 
                    WHERE timestamp > CURRENT_TIMESTAMP - INTERVAL '1 day'
                ) t
                WHERE EXTRACT(EPOCH FROM (timestamp - prev_timestamp)) > 300
                GROUP BY device_id
                HAVING COUNT(*) > 10
            """)
            
            time_gaps = cursor.fetchall()
            results['metrics_time_gaps'] = len(time_gaps)
            
            if time_gaps:
                logger.warning(f"Found significant time gaps in metrics for {len(time_gaps)} devices")
                for device_id, gap_count in time_gaps[:5]:
                    logger.warning(f"Device {device_id}: {gap_count} time gaps")
            else:
                logger.info("✓ Metrics time series consistency verified")
            
        except Exception as e:
            logger.error(f"Error in data consistency validation: {e}")
            results['error'] = str(e)
        finally:
            cursor.close()
        
        return results
    
    def _detect_anomalies(self) -> List[Dict]:
        """이상치 탐지"""
        logger.info("Detecting anomalies...")
        anomalies = []
        
        cursor = self.connection.cursor()
        
        try:
            # 1. 급격한 사용률 변화 탐지
            cursor.execute("""
                SELECT device_id, timestamp, gpu_utilization_pct, prev_utilization,
                       ABS(gpu_utilization_pct - prev_utilization) as utilization_diff
                FROM (
                    SELECT device_id, timestamp, gpu_utilization_pct,
                           LAG(gpu_utilization_pct) OVER (PARTITION BY device_id ORDER BY timestamp) as prev_utilization
                    FROM gpu_usage_metrics 
                    WHERE timestamp > CURRENT_TIMESTAMP - INTERVAL '1 day'
                    AND gpu_utilization_pct IS NOT NULL
                ) t
                WHERE ABS(gpu_utilization_pct - prev_utilization) > 70
                AND prev_utilization IS NOT NULL
                ORDER BY utilization_diff DESC
                LIMIT 10
            """)
            
            utilization_spikes = cursor.fetchall()
            for row in utilization_spikes:
                anomalies.append({
                    'type': 'utilization_spike',
                    'device_id': row[0],
                    'timestamp': row[1].isoformat(),
                    'current_utilization': row[2],
                    'previous_utilization': row[3],
                    'difference': row[4],
                    'severity': 'HIGH' if row[4] > 90 else 'MEDIUM'
                })
            
            # 2. 온도 이상 탐지
            cursor.execute("""
                SELECT device_id, timestamp, temperature_c
                FROM gpu_usage_metrics 
                WHERE timestamp > CURRENT_TIMESTAMP - INTERVAL '1 day'
                AND temperature_c > 90
                ORDER BY temperature_c DESC
                LIMIT 10
            """)
            
            temperature_anomalies = cursor.fetchall()
            for row in temperature_anomalies:
                anomalies.append({
                    'type': 'high_temperature',
                    'device_id': row[0],
                    'timestamp': row[1].isoformat(),
                    'temperature': row[2],
                    'severity': 'CRITICAL' if row[2] > 95 else 'HIGH'
                })
            
            # 3. 전력 소모 이상 탐지
            cursor.execute("""
                SELECT gum.device_id, gum.timestamp, gum.power_draw_w, gm.power_consumption_w
                FROM gpu_usage_metrics gum
                JOIN gpu_devices gd ON gum.device_id = gd.device_id
                JOIN gpu_models gm ON gd.model_id = gm.model_id
                WHERE gum.timestamp > CURRENT_TIMESTAMP - INTERVAL '1 day'
                AND gum.power_draw_w > gm.power_consumption_w * 1.2
                ORDER BY (gum.power_draw_w / gm.power_consumption_w) DESC
                LIMIT 10
            """)
            
            power_anomalies = cursor.fetchall()
            for row in power_anomalies:
                anomalies.append({
                    'type': 'power_anomaly',
                    'device_id': row[0],
                    'timestamp': row[1].isoformat(),
                    'actual_power': row[2],
                    'expected_power': row[3],
                    'severity': 'HIGH'
                })
            
            logger.info(f"Found {len(anomalies)} anomalies")
            
        except Exception as e:
            logger.error(f"Error in anomaly detection: {e}")
            anomalies.append({
                'type': 'detection_error',
                'error': str(e),
                'severity': 'CRITICAL'
            })
        finally:
            cursor.close()
        
        return anomalies
    
    def validate_nvidia_smi_availability(self) -> Dict[str, any]:
        """nvidia-smi 가용성 검증"""
        logger.info("Validating nvidia-smi availability...")
        
        try:
            import subprocess
            result = subprocess.run(['nvidia-smi', '--query-gpu=name', '--format=csv,noheader'], 
                                  capture_output=True, text=True, timeout=10)
            
            if result.returncode == 0:
                gpu_names = result.stdout.strip().split('\n')
                return {
                    'available': True,
                    'gpu_count': len(gpu_names),
                    'gpu_names': gpu_names,
                    'error': None
                }
            else:
                return {
                    'available': False,
                    'error': result.stderr,
                    'gpu_count': 0
                }
                
        except subprocess.TimeoutExpired:
            return {
                'available': False,
                'error': 'nvidia-smi command timed out',
                'gpu_count': 0
            }
        except FileNotFoundError:
            return {
                'available': False,
                'error': 'nvidia-smi command not found',
                'gpu_count': 0
            }
        except Exception as e:
            return {
                'available': False,
                'error': str(e),
                'gpu_count': 0
            }
    
    def generate_validation_report(self, results: Dict) -> str:
        """검증 결과 보고서 생성"""
        logger.info("Generating validation report...")
        
        timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        
        report = f"""
=== GPU 메트릭 검증 보고서 ===
생성 시간: {timestamp}

=== API 엔드포인트 검증 ===
"""
        
        api_results = results.get('api_validation', {})
        for endpoint, status in api_results.items():
            status_icon = "✓" if status else "✗"
            report += f"{status_icon} {endpoint}\n"
        
        report += "\n=== 데이터 무결성 검증 ===\n"
        integrity_results = results.get('data_validation', {}).get('data_integrity', {})
        for check, value in integrity_results.items():
            if 'error' not in check:
                report += f"{check}: {value}\n"
        
        report += "\n=== 데이터 정확성 검증 ===\n"
        accuracy_results = results.get('data_validation', {}).get('data_accuracy', {})
        for check, value in accuracy_results.items():
            if 'error' not in check:
                report += f"{check}: {value}\n"
        
        report += "\n=== 데이터 일관성 검증 ===\n"
        consistency_results = results.get('data_validation', {}).get('data_consistency', {})
        for check, value in consistency_results.items():
            if 'error' not in check:
                report += f"{check}: {value}\n"
        
        report += "\n=== 이상치 탐지 ===\n"
        anomalies = results.get('data_validation', {}).get('anomalies', [])
        if anomalies:
            for anomaly in anomalies[:10]:  # 최대 10개만 표시
                report += f"- {anomaly['type']}: {anomaly.get('device_id', 'N/A')} "
                report += f"(심각도: {anomaly.get('severity', 'UNKNOWN')})\n"
        else:
            report += "이상치가 발견되지 않았습니다.\n"
        
        report += "\n=== nvidia-smi 가용성 ===\n"
        nvidia_smi = results.get('nvidia_smi_validation', {})
        if nvidia_smi.get('available'):
            report += f"✓ nvidia-smi 사용 가능 (GPU 개수: {nvidia_smi.get('gpu_count', 0)})\n"
        else:
            report += f"✗ nvidia-smi 사용 불가: {nvidia_smi.get('error', 'Unknown error')}\n"
        
        report += "\n=== 검증 완료 ===\n"
        
        return report
    
    def run_validation(self) -> Dict[str, any]:
        """전체 검증 실행"""
        logger.info("Starting GPU metrics validation...")
        
        results = {
            'timestamp': datetime.now().isoformat(),
            'validation_version': '1.0.0'
        }
        
        # 1. API 엔드포인트 검증
        results['api_validation'] = self.validate_api_endpoints()
        
        # 2. nvidia-smi 가용성 검증
        results['nvidia_smi_validation'] = self.validate_nvidia_smi_availability()
        
        # 3. 데이터베이스 연결 및 데이터 검증
        if self.connect_database():
            results['data_validation'] = self.validate_gpu_metrics_data()
        else:
            results['data_validation'] = {'error': 'Failed to connect to database'}
        
        # 4. 보고서 생성
        results['report'] = self.generate_validation_report(results)
        
        logger.info("GPU metrics validation completed")
        return results
    
    def __del__(self):
        """소멸자 - 데이터베이스 연결 정리"""
        if self.connection:
            self.connection.close()

def load_config(config_file: str) -> Dict:
    """설정 파일 로드"""
    try:
        with open(config_file, 'r', encoding='utf-8') as f:
            return json.load(f)
    except Exception as e:
        logger.error(f"Failed to load config file {config_file}: {e}")
        return {}

def main():
    """메인 함수"""
    parser = argparse.ArgumentParser(description='GPU 메트릭 검증 도구')
    parser.add_argument('--config', '-c', default='/etc/gpu-management/validation.json',
                       help='설정 파일 경로')
    parser.add_argument('--output', '-o', default='/var/log/gpu-management/validation-report.txt',
                       help='보고서 출력 파일 경로')
    parser.add_argument('--api-only', action='store_true',
                       help='API 검증만 수행')
    parser.add_argument('--data-only', action='store_true',
                       help='데이터 검증만 수행')
    parser.add_argument('--verbose', '-v', action='store_true',
                       help='상세 로그 출력')
    
    args = parser.parse_args()
    
    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)
    
    # 설정 로드
    config = load_config(args.config)
    if not config:
        # 기본 설정 사용
        config = {
            'api_base_url': 'http://localhost:8080/k8s-monitor/api/v1',
            'database': {
                'type': 'postgresql',
                'host': 'localhost',
                'port': 5432,
                'database': 'gpu_management',
                'username': 'gpu_admin',
                'password': 'password'
            }
        }
        logger.warning("Using default configuration")
    
    # 검증기 생성 및 실행
    validator = MetricsValidator(config)
    
    try:
        if args.api_only:
            results = {
                'api_validation': validator.validate_api_endpoints(),
                'nvidia_smi_validation': validator.validate_nvidia_smi_availability()
            }
        elif args.data_only:
            if validator.connect_database():
                results = {
                    'data_validation': validator.validate_gpu_metrics_data()
                }
            else:
                logger.error("Failed to connect to database for data validation")
                sys.exit(1)
        else:
            results = validator.run_validation()
        
        # 보고서 저장
        report = validator.generate_validation_report(results)
        
        try:
            with open(args.output, 'w', encoding='utf-8') as f:
                f.write(report)
            logger.info(f"Validation report saved to {args.output}")
        except Exception as e:
            logger.error(f"Failed to save report to {args.output}: {e}")
            print(report)  # 파일 저장 실패시 콘솔에 출력
        
        # 검증 결과를 JSON으로도 저장
        json_output = args.output.replace('.txt', '.json')
        try:
            with open(json_output, 'w', encoding='utf-8') as f:
                json.dump(results, f, indent=2, ensure_ascii=False, default=str)
            logger.info(f"Validation results saved to {json_output}")
        except Exception as e:
            logger.error(f"Failed to save JSON results: {e}")
        
        # 오류나 경고가 있는 경우 적절한 종료 코드 반환
        has_errors = False
        
        # API 검증 실패 확인
        api_results = results.get('api_validation', {})
        if any(not status for status in api_results.values()):
            has_errors = True
        
        # 데이터 검증 오류 확인
        data_validation = results.get('data_validation', {})
        if 'error' in data_validation:
            has_errors = True
        
        # nvidia-smi 사용 불가 확인
        nvidia_smi = results.get('nvidia_smi_validation', {})
        if not nvidia_smi.get('available', False):
            has_errors = True
        
        # 이상치 확인
        anomalies = data_validation.get('anomalies', [])
        critical_anomalies = [a for a in anomalies if a.get('severity') == 'CRITICAL']
        if critical_anomalies:
            has_errors = True
        
        if has_errors:
            logger.warning("Validation completed with errors or warnings")
            sys.exit(1)
        else:
            logger.info("Validation completed successfully")
            sys.exit(0)
    
    except KeyboardInterrupt:
        logger.info("Validation interrupted by user")
        sys.exit(130)
    except Exception as e:
        logger.error(f"Validation failed with unexpected error: {e}")
        sys.exit(2)

if __name__ == '__main__':
    main()
