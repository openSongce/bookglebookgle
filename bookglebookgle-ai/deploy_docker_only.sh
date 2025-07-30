#!/bin/bash

# ==============================================================================
# BGBG AI Server Docker만 사용 배포 스크립트 (Docker Compose 없이)
# ==============================================================================

set -e

echo "🚀 BGBG AI Server Docker 배포 시작 (Compose 없이)"
echo "================================================"
echo "📍 새 포트 설정:"
echo "   - gRPC: 50505"
echo "   - REST: 8789"
echo "================================================"

# 1. 환경 체크
echo "🔍 1. 환경 체크 중..."

if ! command -v docker &> /dev/null; then
    echo "❌ Docker가 설치되지 않았습니다."
    exit 1
fi

echo "✅ Docker 환경 확인 완료"

# 2. 포트 체크
echo "🔍 2. 포트 충돌 체크 중..."

check_port() {
    local port=$1
    local service_name=$2
    
    if sudo netstat -tlnp | grep -q ":${port} "; then
        echo "⚠️  포트 ${port} (${service_name})이 사용 중입니다."
        echo "   사용 중인 프로세스:"
        sudo netstat -tlnp | grep ":${port} "
        read -p "   계속 진행하시겠습니까? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            echo "❌ 배포가 취소되었습니다."
            exit 1
        fi
    else
        echo "✅ 포트 ${port} (${service_name}) 사용 가능"
    fi
}

check_port "50505" "gRPC"
check_port "8789" "REST API"

# 3. 환경변수 파일 확인
echo "🔧 3. 환경변수 파일 확인 중..."

if [ ! -f "ec2_new_deployment.env" ]; then
    echo "❌ 환경변수 파일 'ec2_new_deployment.env'를 찾을 수 없습니다."
    exit 1
fi

echo "✅ 환경변수 파일 확인 완료"

# 4. Docker 이미지 빌드
echo "🔨 4. Docker 이미지 빌드 중..."

# 기존 컨테이너 정리
if docker ps -a | grep -q "bgbg-ai-service-new"; then
    echo "🛑 기존 컨테이너 중지 및 제거 중..."
    docker stop bgbg-ai-service-new || true
    docker rm bgbg-ai-service-new || true
fi

# 이미지 빌드
echo "🏗️  새 이미지 빌드 중..."
docker build -t bgbg-ai:new . --no-cache

echo "✅ Docker 이미지 빌드 완료"

# 5. 컨테이너 실행
echo "🚀 5. 새 컨테이너 실행 중..."

docker run -d \
    --name bgbg-ai-service-new \
    --restart unless-stopped \
    -p 50505:50505 \
    -p 8789:8789 \
    --env-file ec2_new_deployment.env \
    -e SERVER_PORT=50505 \
    -e FASTAPI_PORT=8789 \
    -e SERVER_HOST=0.0.0.0 \
    -e DEBUG=false \
    -e LOG_LEVEL=INFO \
    -v $(pwd)/data:/app/data \
    -v $(pwd)/logs:/app/logs \
    --memory=4g \
    --cpus=2 \
    bgbg-ai:new

echo "⏳ 서비스 시작 대기 중 (30초)..."
sleep 30

# 6. 서비스 상태 확인
echo "🔍 6. 서비스 상태 확인 중..."

if docker ps | grep -q "bgbg-ai-service-new"; then
    echo "✅ 컨테이너가 정상적으로 실행 중입니다."
else
    echo "❌ 컨테이너 실행에 실패했습니다."
    echo "📋 컨테이너 로그:"
    docker logs bgbg-ai-service-new --tail 50
    exit 1
fi

# 포트 응답 체크
echo "🌐 포트 응답 체크 중..."

# REST API 체크
if curl -f -s "http://localhost:8789/health" > /dev/null; then
    echo "✅ REST API (8789) 응답 정상"
else
    echo "⚠️  REST API (8789) 응답 없음 (정상 시작 중일 수 있음)"
fi

# gRPC 포트 체크
if timeout 5 bash -c '</dev/tcp/localhost/50505'; then
    echo "✅ gRPC (50505) 포트 열림"
else
    echo "⚠️  gRPC (50505) 포트 응답 없음 (정상 시작 중일 수 있음)"
fi

# 7. 배포 완료
echo ""
echo "🎉 Docker 배포 완료!"
echo "=============================================="
echo "📍 서비스 정보:"
echo "   - 컨테이너명: bgbg-ai-service-new"
echo "   - gRPC 포트: 50505"
echo "   - REST API 포트: 8789"
echo ""
echo "🔗 접속 URL:"
echo "   - Health Check: http://localhost:8789/health"
echo "   - API Docs: http://localhost:8789/docs"
echo "   - gRPC: localhost:50505"
echo ""
echo "📋 유용한 명령어:"
echo "   - 로그 확인: docker logs bgbg-ai-service-new -f"
echo "   - 컨테이너 상태: docker ps | grep bgbg-ai-service-new"
echo "   - 컨테이너 중지: docker stop bgbg-ai-service-new"
echo "   - 컨테이너 재시작: docker restart bgbg-ai-service-new"
echo ""
echo "⚠️  기존 서비스 (50052, 8000)와 병행 운영 중입니다."
echo "==============================================" 