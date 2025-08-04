#!/bin/bash

# EC2 Docker 기반 배포 스크립트 for BGBG AI Server
# Ubuntu 20.04/22.04 LTS 기준

set -e

echo "BGBG AI Server Docker Deployment Script"
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

log_info "Starting BGBG AI Server Docker deployment..."

# 1. 시스템 업데이트 (apt_pkg 에러 무시)
log_info "Updating system packages..."
sudo apt update || log_warning "Some package updates failed, continuing..."

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
    ufw

# 3. Docker 설치
log_info "Installing Docker..."
if ! command -v docker &> /dev/null; then
    # Docker 공식 GPG 키 추가
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
    
    # Docker 저장소 추가
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
    
    # Docker 설치
    sudo apt update
    sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    
    # Docker 서비스 시작
    sudo systemctl start docker
    sudo systemctl enable docker
    
    # 현재 사용자를 docker 그룹에 추가
    sudo usermod -aG docker $USER
    
    log_success "Docker installed successfully"
else
    log_warning "Docker is already installed"
fi

# 4. Docker Compose 설치
log_info "Installing Docker Compose..."
if ! command -v docker-compose &> /dev/null; then
    sudo curl -L "https://github.com/docker/compose/releases/download/v2.20.0/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
    sudo chmod +x /usr/local/bin/docker-compose
    log_success "Docker Compose installed successfully"
else
    log_warning "Docker Compose is already installed"
fi

# 5. 프로젝트 디렉토리 설정
PROJECT_DIR="/home/ubuntu/bgbgaiai"
log_info "Setting up project directory at $PROJECT_DIR..."
sudo mkdir -p $PROJECT_DIR
sudo chown $USER:$USER $PROJECT_DIR

# 현재 디렉토리의 코드를 프로젝트 디렉토리로 복사
cp -r . $PROJECT_DIR/
cd $PROJECT_DIR

# 6. Docker Compose 파일 생성 (없는 경우)
if [ ! -f "docker-compose.yml" ]; then
    log_info "Creating docker-compose.yml..."
    cat > docker-compose.yml << 'EOF'
version: '3.8'

services:
  bgbgaiai:
    build: .
    container_name: bgbgaiai-server
    ports:
      - "50505:50505"  # gRPC
      - "8126:8126"    # HTTP
    volumes:
      - ./data:/app/data
      - ./logs:/app/logs
    env_file:
      - .env
    environment:
      - GRPC_PORT=50505
      - HTTP_PORT=8126
      - LOG_LEVEL=INFO
      - OCR_TIMEOUT=60
      - BATCH_SIZE=2
      - USE_CACHE=true
      # Redis 연결 설정 (명시적으로 덮어쓰기)
      - DATABASE__REDIS_HOST=redis
      - DATABASE__REDIS_PORT=6379
      - DATABASE__REDIS_PASSWORD=bgbg_redis_2024!
      - DATABASE__REDIS_DB=0
    restart: unless-stopped
    depends_on:
      redis:
        condition: service_healthy
    networks:
      - bgbg-network
    healthcheck:
      test: ["CMD", "python", "-c", "import requests; requests.get('http://localhost:8126/health', timeout=5)"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s

  redis:
    image: redis:7-alpine
    container_name: bgbg-redis
    ports:
      - "6382:6379"  # 외부 접근용 포트 매핑
    volumes:
      - redis_data:/data
    command: redis-server --appendonly yes --requirepass bgbg_redis_2024!
    restart: unless-stopped
    networks:
      - bgbg-network
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "bgbg_redis_2024!", "ping"]
      interval: 10s
      timeout: 3s
      retries: 3
      start_period: 30s

networks:
  bgbg-network:
    driver: bridge

volumes:
  redis_data:
EOF
    log_success "docker-compose.yml created"
fi

# 7. Dockerfile 생성 (없는 경우)
if [ ! -f "Dockerfile" ]; then
    log_info "Creating Dockerfile..."
    cat > Dockerfile << 'EOF'
FROM python:3.11-slim

# 시스템 의존성 설치
RUN apt-get update && apt-get install -y \
    build-essential \
    libgl1-mesa-glx \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender-dev \
    libgomp1 \
    wget \
    && rm -rf /var/lib/apt/lists/*

# 작업 디렉토리 설정
WORKDIR /app

# Python 의존성 복사 및 설치
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 애플리케이션 코드 복사
COPY . .

# 로그 및 데이터 디렉토리 생성
RUN mkdir -p /app/logs /app/data

# 포트 노출
EXPOSE 50505 8126

# 헬스체크
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD python -c "import requests; requests.get('http://localhost:8126/health', timeout=5)" || exit 1

# 애플리케이션 시작
CMD ["python", "main.py"]
EOF
    log_success "Dockerfile created"
fi

# 8. 환경 변수 파일 생성
log_info "Creating environment file..."
cat > .env << 'EOF'
# BGBG AI Server Environment Configuration
GRPC_PORT=50505
HTTP_PORT=8126
LOG_LEVEL=INFO

# OCR 최적화 설정
OCR_TIMEOUT=60
BATCH_SIZE=2
USE_CACHE=true

# Redis 설정 (Docker 환경)
DATABASE__REDIS_HOST=redis
DATABASE__REDIS_PORT=6379
DATABASE__REDIS_PASSWORD=bgbg_redis_2024!
DATABASE__REDIS_DB=0
DATABASE__REDIS_MAX_CONNECTIONS=20
DATABASE__REDIS_SOCKET_TIMEOUT=5
DATABASE__REDIS_SOCKET_CONNECT_TIMEOUT=5

# AI 서비스 키 (실제 키로 업데이트 필요)
OPENAI_API_KEY=your_openai_key_here
ANTHROPIC_API_KEY=your_anthropic_key_here
GOOGLE_API_KEY=your_google_key_here
EOF

# 9. 방화벽 설정
log_info "Configuring firewall..."
sudo ufw --force enable
sudo ufw allow ssh
sudo ufw allow 50505/tcp  # gRPC port
sudo ufw allow 8126/tcp   # HTTP port
sudo ufw allow 6382/tcp   # Redis port
sudo ufw allow 80/tcp     # Nginx (선택사항)
sudo ufw allow 443/tcp    # HTTPS (선택사항)
log_success "Firewall configured"

# 10. 기존 컨테이너 정리
log_info "Cleaning up existing containers..."
docker-compose down || true
docker system prune -f || true

# 11. Docker 이미지 빌드
log_info "Building Docker image..."
docker-compose build --no-cache

# 12. 컨테이너 시작
log_info "Starting containers..."
docker-compose up -d

# 13. 컨테이너 상태 확인
log_info "Checking container status..."
sleep 30
docker-compose ps

# 14. 로그 확인
log_info "Checking initial logs..."
docker-compose logs --tail=50

# 15. 헬스체크 스크립트 생성
log_info "Creating health check script..."
sudo tee /usr/local/bin/bgbg-docker-health > /dev/null <<'EOF'
#!/bin/bash

echo "BGBG AI Docker Health Check"
echo "=========================="
echo "Date: $(date)"
echo ""

cd /home/ubuntu/bgbgaiai

# Docker 컨테이너 상태 확인
echo "Container Status:"
docker-compose ps

echo ""
echo "Container Health:"
docker-compose exec -T bgbgaiai python -c "print('AI Server is running')" 2>/dev/null || echo "AI Server not responding"
docker-compose exec -T redis redis-cli -a "bgbg_redis_2024!" ping 2>/dev/null || echo "Redis not responding"

echo ""
echo "Resource Usage:"
docker stats --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}" bgbgaiai-server bgbg-redis

echo ""
echo "API Health Check:"
if curl -s http://localhost:8126/health > /dev/null; then
    echo "   HTTP API is responding"
else
    echo "   HTTP API is not responding"
fi

echo ""
echo "Recent Logs:"
docker-compose logs --tail=10 bgbgaiai
EOF

sudo chmod +x /usr/local/bin/bgbg-docker-health

# 16. 시스템 서비스 파일 생성 (자동 시작용)
log_info "Creating systemd service..."
sudo tee /etc/systemd/system/bgbgaiai-docker.service > /dev/null <<EOF
[Unit]
Description=BGBG AI Docker Service
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/home/ubuntu/bgbgaiai
ExecStart=/usr/local/bin/docker-compose up -d
ExecStop=/usr/local/bin/docker-compose down
TimeoutStartSec=0

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable bgbgaiai-docker

# 17. 최종 상태 확인
log_info "Performing final health checks..."
sleep 10

# 컨테이너 상태 확인
if docker-compose ps | grep -q "Up"; then
    log_success "Containers are running"
else
    log_error "Some containers are not running"
    docker-compose logs
fi

# API 응답 확인
if curl -s http://localhost:8126/health > /dev/null; then
    log_success "API is responding"
else
    log_warning "API may still be starting up"
fi

echo ""
log_success "BGBG AI Server Docker deployment completed!"
echo ""
echo "Deployment Summary:"
echo "   - Project Directory: $PROJECT_DIR"
echo "   - gRPC Server: $(curl -s ifconfig.me):50505"
echo "   - HTTP API: $(curl -s ifconfig.me):8126"
echo "   - Redis: localhost:6382 (password: bgbg_redis_2024!)"
echo ""
echo "Useful Commands:"
echo "   - Health check: /usr/local/bin/bgbg-docker-health"
echo "   - View logs: cd $PROJECT_DIR && docker-compose logs -f"
echo "   - Restart: cd $PROJECT_DIR && docker-compose restart"
echo "   - Stop: cd $PROJECT_DIR && docker-compose down"
echo "   - Start: cd $PROJECT_DIR && docker-compose up -d"
echo ""
echo "Test Commands:"
echo "   - HTTP: curl http://localhost:8126/health"
echo "   - gRPC: Use your test scripts with localhost:50505"
echo ""
echo "Don't forget to:"
echo "   1. Update API keys in $PROJECT_DIR/.env"
echo "   2. Configure SSL certificate for production"
echo "   3. Set up monitoring and backups"
echo "   4. Test the optimized PaddleOCR performance"