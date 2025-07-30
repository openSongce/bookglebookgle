# Dockerfile

# 1. 베이스 이미지 선택
FROM python:3.11

# 2. 시스템 패키지 설치 (Tesseract OCR)
RUN apt-get update && apt-get install -y \
    tesseract-ocr \
    tesseract-ocr-kor \
    tesseract-ocr-eng \
    libgl1-mesa-glx \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

# 3. 작업 디렉터리 설정
WORKDIR /app

# 4. Python 의존성 파일 복사 및 설치
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 5. 소스 코드 복사
COPY . .

# 6. 포트 노출
EXPOSE 8789 50505

# 7. 컨테이너 실행 명령어
CMD ["python", "main.py"]