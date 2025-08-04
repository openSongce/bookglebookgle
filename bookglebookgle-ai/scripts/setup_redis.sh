#!/bin/bash

# Redis 서버 설치 및 설정 스크립트 for Ubuntu/EC2
# BGBG AI Server Chat History 기능용

set -e

echo "🔧 Starting Redis setup for BGBG AI Server..."

# 시스템 업데이트
echo "📦 Updating system packages..."
sudo apt update
sudo apt upgrade -y

# Redis 설치
echo "📦 Installing Redis server..."
sudo apt install redis-server -y

# Redis 설정 백업
echo "💾 Backing up original Redis configuration..."
sudo cp /etc/redis/redis.conf /etc/redis/redis.conf.backup

# Redis 설정 파일 수정
echo "⚙️ Configuring Redis for chat history..."

# 메모리 정책 설정 (채팅 기록용 최적화)
sudo tee -a /etc/redis/redis.conf > /dev/null <<EOF

# BGBG AI Chat History Configuration
# 메모리 제한 설정 (512MB)
maxmemory 512mb

# 메모리 정책: TTL이 있는 키 중 가장 적게 사용된 것부터 삭제
maxmemory-policy allkeys-lru

# 지속성 설정 (채팅 기록은 임시 데이터이므로 최소화)
save 900 1
save 300 10
save 60 10000

# AOF 비활성화 (성능 우선)
appendonly no

# 네트워크 설정
bind 127.0.0.1
port 6379

# 보안 설정
requirepass bgbg_redis_2024!

# 로그 레벨
loglevel notice
logfile /var/log/redis/redis-server.log

# 클라이언트 연결 제한
maxclients 100

# 타임아웃 설정
timeout 300

# TCP keepalive
tcp-keepalive 300

# 데이터베이스 수
databases 16
EOF

# Redis 사용자 및 권한 설정
echo "🔐 Setting up Redis user and permissions..."
sudo usermod -a -G redis $USER

# 로그 디렉토리 생성
sudo mkdir -p /var/log/redis
sudo chown redis:redis /var/log/redis

# Redis 서비스 활성화 및 시작
echo "🚀 Starting Redis service..."
sudo systemctl enable redis-server
sudo systemctl restart redis-server

# Redis 상태 확인
echo "✅ Checking Redis status..."
sudo systemctl status redis-server --no-pager

# Redis 연결 테스트
echo "🔍 Testing Redis connection..."
redis-cli -a "bgbg_redis_2024!" ping

# 메모리 정보 확인
echo "📊 Redis memory info:"
redis-cli -a "bgbg_redis_2024!" info memory | grep -E "(used_memory_human|maxmemory_human|maxmemory_policy)"

# 방화벽 설정 (로컬 접근만 허용)
echo "🔥 Configuring firewall..."
sudo ufw allow from 127.0.0.1 to any port 6379

echo "✅ Redis setup completed successfully!"
echo ""
echo "📋 Redis Configuration Summary:"
echo "   - Port: 6379"
echo "   - Password: bgbg_redis_2024!"
echo "   - Max Memory: 512MB"
echo "   - Memory Policy: allkeys-lru"
echo "   - Bind: 127.0.0.1 (localhost only)"
echo ""
echo "🔧 To connect to Redis:"
echo "   redis-cli -a 'bgbg_redis_2024!'"
echo ""
echo "📝 Environment variables to set:"
echo "   export DATABASE__REDIS_HOST=localhost"
echo "   export DATABASE__REDIS_PORT=6379"
echo "   export DATABASE__REDIS_PASSWORD=bgbg_redis_2024!"
echo "   export DATABASE__REDIS_DB=0"