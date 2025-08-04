#!/bin/bash

# EC2 배포 스크립트 for BGBG AI Server with Chat History
# Ubuntu 20.04/22.04 LTS 기준

set -e

echo "🚀 BGBG AI Server EC2 Deployment Script"
echo "========================================"

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

log_info "Starting BGBG AI Server deployment..."

# 1. 시스템 업데이트
log_info "Updating system packages..."
sudo apt update
sudo apt upgrade -y

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
    bc

# 3. Python 3.11 설치
log_info "Installing Python 3.11..."
sudo add-apt-repository ppa:deadsnakes/ppa -y
sudo apt update
sudo apt install -y python3.11 python3.11-venv python3.11-dev python3-pip

# Python 3.11을 기본으로 설정
sudo update-alternatives --install /usr/bin/python3 python3 /usr/bin/python3.11 1

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
    sudo apt install -y redis-server
    
    # Redis 설정
    sudo cp /etc/redis/redis.conf /etc/redis/redis.conf.backup
    
    # Redis 설정 업데이트
    sudo tee /etc/redis/redis.conf > /dev/null <<EOF
# BGBG AI Redis Configuration
bind 127.0.0.1
port 6379
requirepass bgbg_redis_2024!

# Memory settings
maxmemory 512mb
maxmemory-policy allkeys-lru

# Persistence settings (minimal for chat history)
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

# 7. 애플리케이션 코드 배포 (Git에서 클론 또는 파일 복사)
log_info "Deploying application code..."
if [ -d "$PROJECT_DIR/src" ]; then
    log_warning "Application code already exists, backing up..."
    sudo -u $SERVICE_USER cp -r $PROJECT_DIR $PROJECT_DIR.backup.$(date +%Y%m%d_%H%M%S)
fi

# 현재 디렉토리의 코드를 프로젝트 디렉토리로 복사
sudo -u $SERVICE_USER cp -r . $PROJECT_DIR/
sudo chown -R $SERVICE_USER:$SERVICE_USER $PROJECT_DIR

# 8. Python 가상환경 설정
log_info "Setting up Python virtual environment..."
cd $PROJECT_DIR
sudo -u $SERVICE_USER python3 -m venv venv
sudo -u $SERVICE_USER ./venv/bin/pip install --upgrade pip

# 9. Python 의존성 설치
log_info "Installing Python dependencies..."
if [ -f "requirements.txt" ]; then
    sudo -u $SERVICE_USER ./venv/bin/pip install -r requirements.txt
    log_success "Python dependencies installed"
else
    log_error "requirements.txt not found!"
    exit 1
fi

# 10. 환경 변수 설정
log_info "Setting up environment variables..."
sudo -u $SERVICE_USER tee $PROJECT_DIR/.env > /dev/null <<EOF
# BGBG AI Server Environment Configuration
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

# Performance Settings
CHAT_HISTORY__CHAT_REDIS_MEMORY_LIMIT_MB=512
CHAT_HISTORY__CHAT_SESSION_CLEANUP_INTERVAL_MINUTES=30
CHAT_HISTORY__CHAT_INACTIVE_THRESHOLD_MINUTES=30

# Redis Configuration
DATABASE__REDIS_HOST=localhost
DATABASE__REDIS_PORT=6379
DATABASE__REDIS_DB=0
DATABASE__REDIS_PASSWORD=bgbg_redis_2024!
DATABASE__REDIS_MAX_CONNECTIONS=20
DATABASE__REDIS_SOCKET_TIMEOUT=5
DATABASE__REDIS_SOCKET_CONNECT_TIMEOUT=5

# AI Settings (update with your actual keys)
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

log_success "Environment variables configured"

# 11. Supervisor 설정 (프로세스 관리)
log_info "Setting up Supervisor for process management..."
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
environment=PATH="$PROJECT_DIR/venv/bin"

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
environment=PATH="$PROJECT_DIR/venv/bin"
EOF

# 로그 디렉토리 생성
sudo mkdir -p /var/log/bgbg-ai
sudo chown $SERVICE_USER:$SERVICE_USER /var/log/bgbg-ai

# 12. Nginx 설정 (리버스 프록시)
log_info "Setting up Nginx reverse proxy..."
sudo tee /etc/nginx/sites-available/bgbg-ai > /dev/null <<EOF
server {
    listen 80;
    server_name _;

    # REST API
    location /api/ {
        proxy_pass http://127.0.0.1:8789/;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        
        # Timeout settings
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }

    # Health check endpoint
    location /health {
        proxy_pass http://127.0.0.1:8789/health;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
    }

    # Static files (if any)
    location /static/ {
        alias $PROJECT_DIR/static/;
        expires 1d;
        add_header Cache-Control "public, immutable";
    }

    # Default location
    location / {
        return 200 'BGBG AI Server is running';
        add_header Content-Type text/plain;
    }
}
EOF

# Nginx 사이트 활성화
sudo ln -sf /etc/nginx/sites-available/bgbg-ai /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default

# 13. 방화벽 설정
log_info "Configuring firewall..."
sudo ufw --force enable
sudo ufw allow ssh
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 50505/tcp  # gRPC port

# Redis는 로컬에서만 접근
sudo ufw deny 6379

log_success "Firewall configured"

# 14. 시스템 서비스 시작
log_info "Starting services..."

# Supervisor 재시작
sudo systemctl restart supervisor
sudo systemctl enable supervisor

# Nginx 재시작
sudo nginx -t && sudo systemctl restart nginx
sudo systemctl enable nginx

# 15. 헬스 체크 스크립트 생성
log_info "Creating health check script..."
sudo tee /usr/local/bin/bgbg-health-check > /dev/null <<'EOF'
#!/bin/bash

echo "🏥 BGBG AI Server Health Check"
echo "=============================="
echo "📅 $(date)"
echo ""

# Check Redis
echo "🔍 Redis Status:"
if redis-cli -a "bgbg_redis_2024!" ping > /dev/null 2>&1; then
    echo "   ✅ Redis is running"
else
    echo "   ❌ Redis is down"
fi

# Check Supervisor processes
echo ""
echo "🔍 Application Processes:"
sudo supervisorctl status

# Check Nginx
echo ""
echo "🔍 Nginx Status:"
if sudo systemctl is-active --quiet nginx; then
    echo "   ✅ Nginx is running"
else
    echo "   ❌ Nginx is down"
fi

# Check API endpoint
echo ""
echo "🔍 API Health Check:"
if curl -s http://localhost:8789/health > /dev/null; then
    echo "   ✅ API is responding"
else
    echo "   ❌ API is not responding"
fi

# System resources
echo ""
echo "🔍 System Resources:"
echo "   CPU: $(top -bn1 | grep "Cpu(s)" | awk '{print $2}' | cut -d'%' -f1)% used"
echo "   Memory: $(free | grep Mem | awk '{printf("%.1f%%", $3/$2 * 100.0)}')"
echo "   Disk: $(df -h / | awk 'NR==2{printf "%s", $5}')"

echo ""
echo "=============================="
EOF

sudo chmod +x /usr/local/bin/bgbg-health-check

# 16. 모니터링 스크립트 복사
log_info "Setting up monitoring scripts..."
sudo cp scripts/monitor_redis.sh /usr/local/bin/bgbg-monitor-redis
sudo chmod +x /usr/local/bin/bgbg-monitor-redis

# 17. 로그 로테이션 설정
log_info "Setting up log rotation..."
sudo tee /etc/logrotate.d/bgbg-ai > /dev/null <<EOF
/var/log/bgbg-ai/*.log {
    daily
    missingok
    rotate 7
    compress
    delaycompress
    notifempty
    create 644 $SERVICE_USER $SERVICE_USER
    postrotate
        sudo supervisorctl restart bgbg-ai-server bgbg-ai-grpc
    endscript
}
EOF

# 18. 최종 상태 확인
log_info "Performing final health checks..."
sleep 5

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
fi

# Nginx 상태 확인
if sudo systemctl is-active --quiet nginx; then
    log_success "Nginx is running"
else
    log_error "Nginx is not running"
fi

echo ""
log_success "🎉 BGBG AI Server deployment completed!"
echo ""
echo "📋 Deployment Summary:"
echo "   - Project Directory: $PROJECT_DIR"
echo "   - Service User: $SERVICE_USER"
echo "   - Redis: localhost:6379 (password: bgbg_redis_2024!)"
echo "   - REST API: http://your-server/api/"
echo "   - gRPC: your-server:50505"
echo "   - Health Check: http://your-server/health"
echo ""
echo "🔧 Useful Commands:"
echo "   - Check health: /usr/local/bin/bgbg-health-check"
echo "   - Monitor Redis: /usr/local/bin/bgbg-monitor-redis"
echo "   - View logs: sudo tail -f /var/log/bgbg-ai/server.log"
echo "   - Restart services: sudo supervisorctl restart all"
echo "   - Check processes: sudo supervisorctl status"
echo ""
echo "⚠️  Don't forget to:"
echo "   1. Update API keys in $PROJECT_DIR/.env"
echo "   2. Configure SSL certificate for production"
echo "   3. Set up monitoring and backups"
echo "   4. Review security settings"