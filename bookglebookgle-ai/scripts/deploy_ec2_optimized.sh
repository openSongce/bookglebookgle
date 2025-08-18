#!/bin/bash

# 기존 EC2 배포 스크립트에 PaddleOCR 최적화 적용
# Ubuntu 20.04/22.04 LTS 기준

set +e  # apt_pkg 에러 무시

echo "🚀 BGBG AI Server EC2 Deployment Script (PaddleOCR Optimized)"
echo "============================================================="

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 로그 함수
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 환경 변수 설정
export DEBIAN_FRONTEND=noninteractive
PROJECT_DIR="/opt/bgbg-ai"
SERVICE_USER="bgbg"
PYTHON_VERSION="3.11"

log_info "Starting BGBG AI Server deployment with PaddleOCR optimizations..."

# 1. 시스템 업데이트 (에러 무시)
log_info "Updating system packages..."
sudo apt update || log_warning "Some package updates failed, continuing..."
sudo apt upgrade -y || log_warning "Some package upgrades failed, continuing..."

# 2. 필수 패키지 설치
log_info "Installing essential packages..."
sudo apt install -y \
    curl \
    wget \
    git \
    build-essential \
    software-properties-common \
    apt-transport-https \
    ca-certificates \
    gnupg \
    lsb-release \
    htop \
    vim \
    unzip \
    supervisor \
    nginx \
    ufw \
    bc \
    libgl1-mesa-glx \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender-dev \
    libgomp1 \
    libopencv-dev \
    python3-opencv || log_warning "Some packages failed to install, continuing..."

# 3. Python 3.11 설치
log_info "Installing Python 3.11..."
sudo add-apt-repository ppa:deadsnakes/ppa -y || true
sudo apt update || true
sudo apt install -y python3.11 python3.11-venv python3.11-dev python3-pip || log_error "Python 3.11 installation failed"

# Python 3.11을 기본으로 설정
sudo update-alternatives --install /usr/bin/python3 python3 /usr/bin/python3.11 1 || true

# 4. 서비스 사용자 생성
log_info "Creating service user..."
if ! id "$SERVICE_USER" &>/dev/null; then
    sudo useradd -r -s /bin/bash -d $PROJECT_DIR $SERVICE_USER
    log_success "Service user '$SERVICE_USER' created"
else
    log_warning "Service user '$SERVICE_USER' already exists"
fi

# 5. 프로젝트 디렉토리 생성
log_info "Setting up project directory..."
sudo mkdir -p $PROJECT_DIR
sudo chown $SERVICE_USER:$SERVICE_USER $PROJECT_DIR

# 6. Redis 설치 및 설정
log_info "Setting up Redis..."
if ! command -v redis-server &> /dev/null; then
    sudo apt install -y redis-server || log_error "Redis installation failed"
    
    # Redis 설정
    sudo cp /etc/redis/redis.conf /etc/redis/redis.conf.backup || true
    
    # Redis 설정 업데이트
    sudo tee /etc/redis/redis.conf > /dev/null <<EOF
# BGBG AI Redis Configuration
bind 127.0.0.1
port 6379
requirepass bgbg_redis_2024!

# Memory settings (PaddleOCR 최적화를 위해 증가)
maxmemory 1gb
maxmemory-policy allkeys-lru

# Persistence settings
save 900 1
save 300 10
save 60 10000
appendonly no

# Performance settings
timeout 300
tcp-keepalive 300
maxclients 100

# Logging
loglevel notice
logfile /var/log/redis/redis-server.log
EOF

    sudo systemctl enable redis-server
    sudo systemctl restart redis-server
    log_success "Redis installed and configured"
else
    log_warning "Redis already installed"
fi

# 7. 애플리케이션 코드 배포
log_info "Deploying application code..."
if [ -d "$PROJECT_DIR/src" ]; then
    log_warning "Application code already exists, backing up..."
    sudo -u $SERVICE_USER cp -r $PROJECT_DIR $PROJECT_DIR.backup.$(date +%Y%m%d_%H%M%S) || true
fi

# 현재 디렉토리의 코드를 프로젝트 디렉토리로 복사
sudo -u $SERVICE_USER cp -r . $PROJECT_DIR/ || log_error "Code deployment failed"
sudo chown -R $SERVICE_USER:$SERVICE_USER $PROJECT_DIR

# 8. Python 가상환경 설정
log_info "Setting up Python virtual environment..."
cd $PROJECT_DIR
sudo -u $SERVICE_USER python3 -m venv venv || log_error "Virtual environment creation failed"
sudo -u $SERVICE_USER ./venv/bin/pip install --upgrade pip || log_warning "Pip upgrade failed"

# 9. Python 의존성 설치 (PaddleOCR 최적화)
log_info "Installing Python dependencies with PaddleOCR optimizations..."
if [ -f "requirements.txt" ]; then
    # PaddleOCR 관련 최적화된 설치
    log_info "Installing PaddleOCR with CPU optimizations..."
    sudo -u $SERVICE_USER ./venv/bin/pip install paddlepaddle==2.5.2 -i https://pypi.tuna.tsinghua.edu.cn/simple || log_warning "PaddlePaddle installation failed"
    sudo -u $SERVICE_USER ./venv/bin/pip install paddleocr==2.7.3 || log_warning "PaddleOCR installation failed"
    
    # 기타 의존성 설치
    sudo -u $SERVICE_USER ./venv/bin/pip install -r requirements.txt || log_warning "Some dependencies failed to install"
    
    log_success "Python dependencies installed"
else
    log_error "requirements.txt not found!"
    exit 1
fi

# 10. 환경 변수 설정 (PaddleOCR 최적화 포함)
log_info "Setting up environment variables with PaddleOCR optimizations..."
sudo -u $SERVICE_USER tee $PROJECT_DIR/.env > /dev/null <<EOF
# BGBG AI Server Environment Configuration
# PaddleOCR 최적화 설정
PADDLEOCR_TIMEOUT=60
PADDLEOCR_BATCH_SIZE=2
PADDLEOCR_USE_CACHE=true
PADDLEOCR_USE_MOBILE_MODEL=true
PADDLEOCR_CPU_THREADS=4

# Chat History Settings
CHAT_HISTORY__CHAT_HISTORY_ENABLED=true
CHAT_HISTORY__CHAT_MESSAGE_TTL_HOURS=24
CHAT_HISTORY__CHAT_CONTEXT_TTL_HOURS=1
CHAT_HISTORY__CHAT_PARTICIPANT_TTL_HOURS=2
CHAT_HISTORY__CHAT_META_TTL_HOURS=168

# Context Window Settings
CHAT_HISTORY__CHAT_CONTEXT_WINDOW_SIZE=10
CHAT_HISTORY__CHAT_MAX_TOKENS=2000
CHAT_HISTORY__CHAT_TIME_WINDOW_HOURS=2
CHAT_HISTORY__CHAT_MAX_BOOK_CHUNKS=3

# Performance Settings (PaddleOCR 고려하여 증가)
CHAT_HISTORY__CHAT_REDIS_MEMORY_LIMIT_MB=1024
CHAT_HISTORY__CHAT_SESSION_CLEANUP_INTERVAL_MINUTES=30
CHAT_HISTORY__CHAT_INACTIVE_THRESHOLD_MINUTES=30

# Redis Configuration
DATABASE__REDIS_HOST=localhost
DATABASE__REDIS_PORT=6379
DATABASE__REDIS_DB=0
DATABASE__REDIS_PASSWORD=bgbg_redis_2024!
DATABASE__REDIS_MAX_CONNECTIONS=20
DATABASE__REDIS_SOCKET_TIMEOUT=10
DATABASE__REDIS_SOCKET_CONNECT_TIMEOUT=10

# AI Settings
AI__OPENAI_API_KEY=your_openai_key_here
AI__ANTHROPIC_API_KEY=your_anthropic_key_here
AI__GOOGLE_API_KEY=your_google_key_here

# Server Settings
SERVER_HOST=0.0.0.0
SERVER_PORT=8789
GRPC_PORT=50505

# Logging
LOG_LEVEL=INFO
EOF

log_success "Environment variables configured with PaddleOCR optimizations"

# 11. PaddleOCR 모델 사전 다운로드
log_info "Pre-downloading PaddleOCR models..."
sudo -u $SERVICE_USER tee $PROJECT_DIR/preload_models.py > /dev/null <<'EOF'
#!/usr/bin/env python3
"""
PaddleOCR 모델 사전 다운로드 스크립트
"""
import os
import sys

# 환경 변수 설정
os.environ['CUDA_VISIBLE_DEVICES'] = ''

try:
    from paddleocr import PaddleOCR
    
    print("🔄 PaddleOCR 모델 다운로드 중...")
    
    # 가벼운 모바일 모델로 초기화
    ocr = PaddleOCR(
        use_angle_cls=True,
        lang='korean',
        det_limit_side_len=480,
        rec_batch_num=6,
        use_gpu=False,
        show_log=True
    )
    
    print("✅ PaddleOCR 모델 다운로드 완료")
    
except Exception as e:
    print(f"❌ 모델 다운로드 실패: {e}")
    sys.exit(1)
EOF

# 모델 다운로드 실행
sudo -u $SERVICE_USER ./venv/bin/python $PROJECT_DIR/preload_models.py || log_warning "Model preloading failed"

# 12. Supervisor 설정 (PaddleOCR 최적화)
log_info "Setting up Supervisor with PaddleOCR optimizations..."
sudo tee /etc/supervisor/conf.d/bgbg-ai.conf > /dev/null <<EOF
[program:bgbg-ai-server]
command=$PROJECT_DIR/venv/bin/python main.py
directory=$PROJECT_DIR
user=$SERVICE_USER
autostart=true
autorestart=true
redirect_stderr=true
stdout_logfile=/var/log/bgbg-ai/server.log
stdout_logfile_maxbytes=50MB
stdout_logfile_backups=5
environment=PATH="$PROJECT_DIR/venv/bin",CUDA_VISIBLE_DEVICES="",OMP_NUM_THREADS="4"
priority=999
startsecs=30
stopwaitsecs=60

[program:bgbg-ai-grpc]
command=$PROJECT_DIR/venv/bin/python -c "import asyncio; from src.grpc_server.server import start_grpc_server; asyncio.run(start_grpc_server())"
directory=$PROJECT_DIR
user=$SERVICE_USER
autostart=true
autorestart=true
redirect_stderr=true
stdout_logfile=/var/log/bgbg-ai/grpc.log
stdout_logfile_maxbytes=50MB
stdout_logfile_backups=5
environment=PATH="$PROJECT_DIR/venv/bin",CUDA_VISIBLE_DEVICES="",OMP_NUM_THREADS="4"
priority=999
startsecs=30
stopwaitsecs=60
EOF

# 로그 디렉토리 생성
sudo mkdir -p /var/log/bgbg-ai
sudo chown $SERVICE_USER:$SERVICE_USER /var/log/bgbg-ai

# 13. Nginx 설정 (타임아웃 증가)
log_info "Setting up Nginx with extended timeouts..."
sudo tee /etc/nginx/sites-available/bgbg-ai > /dev/null <<EOF
server {
    listen 80;
    server_name _;

    # 클라이언트 업로드 크기 제한 증가 (PDF 업로드용)
    client_max_body_size 100M;

    # REST API
    location /api/ {
        proxy_pass http://127.0.0.1:8789/;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        
        # PaddleOCR 처리를 위한 타임아웃 증가
        proxy_connect_timeout 120s;
        proxy_send_timeout 120s;
        proxy_read_timeout 120s;
    }

    # Health check endpoint
    location /health {
        proxy_pass http://127.0.0.1:8789/health;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
    }

    # OCR endpoint with extended timeout
    location /ocr {
        proxy_pass http://127.0.0.1:8789/ocr;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        
        # PaddleOCR 처리를 위한 긴 타임아웃
        proxy_connect_timeout 180s;
        proxy_send_timeout 180s;
        proxy_read_timeout 180s;
    }

    # Static files
    location /static/ {
        alias $PROJECT_DIR/static/;
        expires 1d;
        add_header Cache-Control "public, immutable";
    }

    # Default location
    location / {
        return 200 'BGBG AI Server is running (PaddleOCR Optimized)';
        add_header Content-Type text/plain;
    }
}
EOF

# Nginx 사이트 활성화
sudo ln -sf /etc/nginx/sites-available/bgbg-ai /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default

# 14. 방화벽 설정
log_info "Configuring firewall..."
sudo ufw --force enable
sudo ufw allow ssh
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 50505/tcp  # gRPC port

# Redis는 로컬에서만 접근
sudo ufw deny 6379

log_success "Firewall configured"

# 15. 시스템 서비스 시작
log_info "Starting services..."

# Supervisor 재시작
sudo systemctl restart supervisor
sudo systemctl enable supervisor

# Nginx 재시작
sudo nginx -t && sudo systemctl restart nginx || log_warning "Nginx configuration test failed"
sudo systemctl enable nginx

# 16. PaddleOCR 성능 테스트 스크립트 생성
log_info "Creating PaddleOCR performance test script..."
sudo -u $SERVICE_USER tee $PROJECT_DIR/test_paddleocr_performance.py > /dev/null <<'EOF'
#!/usr/bin/env python3
"""
PaddleOCR 성능 테스트 스크립트
"""
import time
import sys
import os

# 환경 변수 설정
os.environ['CUDA_VISIBLE_DEVICES'] = ''

try:
    from paddleocr import PaddleOCR
    from PIL import Image, ImageDraw, ImageFont
    import numpy as np
    
    print("🧪 PaddleOCR 성능 테스트 시작")
    print("=" * 40)
    
    # 테스트 이미지 생성
    image = Image.new('RGB', (400, 200), color='white')
    draw = ImageDraw.Draw(image)
    draw.text((50, 50), "Hello World! 안녕하세요!", fill='black')
    draw.text((50, 100), "PaddleOCR 성능 테스트", fill='black')
    
    image_array = np.array(image)
    
    # 첫 번째 실행 (초기화 포함)
    print("🔄 첫 번째 실행 (초기화 포함)...")
    start_time = time.time()
    ocr = PaddleOCR(use_angle_cls=True, lang='korean', use_gpu=False)
    result1 = ocr.ocr(image_array, cls=True)
    first_time = time.time() - start_time
    print(f"   시간: {first_time:.2f}초")
    print(f"   결과: {len(result1[0]) if result1[0] else 0}개 텍스트 블록")
    
    # 두 번째 실행 (캐시 효과)
    print("\n🚀 두 번째 실행 (캐시 효과)...")
    start_time = time.time()
    result2 = ocr.ocr(image_array, cls=True)
    second_time = time.time() - start_time
    print(f"   시간: {second_time:.2f}초")
    print(f"   결과: {len(result2[0]) if result2[0] else 0}개 텍스트 블록")
    
    # 성능 개선 계산
    improvement = (first_time - second_time) / first_time * 100
    print(f"\n📊 성능 개선: {improvement:.1f}%")
    
    if second_time < 5.0:
        print("✅ 최적화 성공: 5초 이내 처리")
    else:
        print("⚠️  추가 최적화 필요")
        
except Exception as e:
    print(f"❌ 테스트 실패: {e}")
    sys.exit(1)
EOF

sudo chmod +x $PROJECT_DIR/test_paddleocr_performance.py

# 17. 최종 상태 확인
log_info "Performing final health checks..."
sleep 10

# Redis 상태 확인
if redis-cli -a "bgbg_redis_2024!" ping > /dev/null 2>&1; then
    log_success "Redis is running"
else
    log_error "Redis is not running"
fi

# Supervisor 프로세스 확인
if sudo supervisorctl status | grep -q "RUNNING"; then
    log_success "Application processes are running"
else
    log_warning "Some application processes may not be running"
    sudo supervisorctl status
fi

# Nginx 상태 확인
if sudo systemctl is-active --quiet nginx; then
    log_success "Nginx is running"
else
    log_error "Nginx is not running"
fi

echo ""
log_success "🎉 BGBG AI Server deployment with PaddleOCR optimizations completed!"
echo ""
echo "📋 Deployment Summary:"
echo "   - Project Directory: $PROJECT_DIR"
echo "   - Service User: $SERVICE_USER"
echo "   - Redis: localhost:6379 (password: bgbg_redis_2024!)"
echo "   - REST API: http://your-server/api/"
echo "   - gRPC: your-server:50505"
echo "   - Health Check: http://your-server/health"
echo ""
echo "🚀 PaddleOCR Optimizations Applied:"
echo "   - 60초 타임아웃 (기존 10초에서 증가)"
echo "   - 배치 크기 2페이지 (메모리 최적화)"
echo "   - 가벼운 모바일 모델 사용"
echo "   - 인스턴스 캐싱 활성화"
echo "   - CPU 최적화 (4 스레드)"
echo ""
echo "🔧 Useful Commands:"
echo "   - Check health: /usr/local/bin/bgbg-health-check"
echo "   - Test PaddleOCR: sudo -u $SERVICE_USER $PROJECT_DIR/venv/bin/python $PROJECT_DIR/test_paddleocr_performance.py"
echo "   - View logs: sudo tail -f /var/log/bgbg-ai/server.log"
echo "   - Restart services: sudo supervisorctl restart all"
echo "   - Check processes: sudo supervisorctl status"
echo ""
echo "⚠️  Don't forget to:"
echo "   1. Update API keys in $PROJECT_DIR/.env"
echo "   2. Test OCR performance with real PDFs"
echo "   3. Monitor memory usage during peak loads"
echo "   4. Configure SSL certificate for production"