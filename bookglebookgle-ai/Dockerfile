# Dockerfile for BGBG AI Server
# 다중 OCR 엔진 지원 및 호환성 문제 해결

# 1. 베이스 이미지 선택 (Python 3.11 slim for better performance)
FROM python:3.11-slim

# 2. 메타데이터 설정
LABEL maintainer="BGBG AI Team"
LABEL description="BGBG AI Server with Multi-OCR Engine Support"
LABEL version="2.0"

# 3. 환경 변수 설정
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    DEBIAN_FRONTEND=noninteractive \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

# 4. 시스템 패키지 업데이트 및 OCR 엔진 설치
RUN apt-get update && apt-get install -y --no-install-recommends \
    # 기본 시스템 도구
    wget \
    curl \
    git \
    build-essential \
    # Tesseract OCR (기본 설치)
    tesseract-ocr \
    # OpenCV 및 이미지 처리 라이브러리
    libgl1-mesa-glx \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender-dev \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/* \
    && apt-get clean

# 5. Tesseract 언어팩 별도 설치 (오류 방지)
RUN apt-get update && apt-get install -y --no-install-recommends \
    tesseract-ocr-eng \
    tesseract-ocr-kor \
    && apt-get install -y --no-install-recommends tesseract-ocr-chi-sim || true \
    && apt-get install -y --no-install-recommends tesseract-ocr-jpn || true \
    && rm -rf /var/lib/apt/lists/* \
    && apt-get clean

# 6. 작업 디렉터리 설정
WORKDIR /app

# 7. pip 업그레이드
RUN pip install --upgrade pip setuptools wheel

# 8. Python 의존성 파일 복사 및 설치 (단계별 설치로 최적화)
COPY requirements.txt .

# 중요: paddlepaddle을 먼저 설치하여 의존성 충돌 최소화
RUN pip install --no-cache-dir paddlepaddle>=2.6.0

# 나머지 Python 의존성 설치
RUN pip install --no-cache-dir -r requirements.txt

# 9. 소스 코드 복사
COPY . .

# 10. 포트 노출
EXPOSE 8789 50505

# 11. 헬스체크 추가
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8789/health || exit 1

# 12. 컨테이너 실행 명령어 (root 사용자로 실행 - 권한 문제 방지)
CMD ["python", "main.py"]