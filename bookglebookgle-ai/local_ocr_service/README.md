# Tesseract OCR gRPC 서비스

Tesseract OCR 엔진을 사용하여 PDF 파일의 텍스트를 추출하는 고성능 gRPC 서비스입니다.

## 주요 기능

- **gRPC 기반 OCR 서비스**: PDF 파일을 스트림으로 받아 텍스트를 추출하는 gRPC 인터페이스를 제공합니다.
- **Tesseract OCR 엔진**: 안정적인 텍스트 인식을 위해 Tesseract를 사용하며, 한글 및 영문 인식을 지원합니다.
- **비동기 및 병렬 처리**: `asyncio`를 기반으로 비동기 처리를 지원하며, `ThreadPoolExecutor`와 `ProcessPoolExecutor`를 활용하여 여러 페이지를 병렬로 처리하여 성능을 극대화합니다.
- **고급 이미지 전처리**: OCR 정확도를 높이기 위해 이미지 해상도에 따라 동적으로 최적화된 전처리 파이프라인(노이즈 제거, 대비 향상, 선명화 등)을 적용합니다.
- **자원 관리**: 서버 시작 시 포트 충돌을 감지하고 자동으로 해결하며, 서비스 통계 로깅 및 리소스 정리 기능을 포함합니다.

## 파일 구조

- **`tesseract_grpc_server.py`**: gRPC 서버를 시작하는 메인 파일입니다. 클라이언트 요청을 받아 OCR 처리를 오케스트레이션합니다.
- **`tesseract_ocr.py`**: Tesseract OCR의 핵심 로직이 담긴 파일입니다. PDF 페이지를 이미지로 변환하고, 전처리 후 Tesseract를 통해 텍스트를 추출합니다.
- **`ai_service_pb2.py`**: Protobuf로 정의된 gRPC 메시지 객체들이 포함된 파일입니다.
- **`ai_service_pb2_grpc.py`**: gRPC 서비스의 규격(stub)과 서버 로직이 포함된 파일입니다.
- **`ai_service_pb2.pyi`**: `ai_service_pb2.py`에 대한 타입 힌트를 제공하는 파일입니다.

## 시스템 요구사항

- Python 3.8 이상
- Tesseract OCR 엔진 설치 (시스템 PATH에 추가 권장)

## 설치 방법

1.  **Tesseract OCR 설치**
    -   운영체제에 맞는 Tesseract를 설치합니다.
    -   Windows: [Tesseract at UB Mannheim](https://github.com/UB-Mannheim/tesseract/wiki)
    -   macOS: `brew install tesseract`
    -   Linux: `sudo apt-get install tesseract-ocr`
    -   설치 후 `tesseract` 명령어가 시스템 어디에서나 실행되도록 PATH 환경 변수를 설정해주세요.

2.  **Python 라이브러리 설치**
    -   필요한 라이브러리를 `requirements.txt` 파일을 통해 설치합니다.
        ```bash
        pip install -r requirements.txt
        ```

## 사용 방법

gRPC 서버를 시작하려면 다음 명령어를 실행하세요.

```bash
python tesseract_grpc_server.py
```

서버가 시작되면 `0.0.0.0:4738` 주소에서 요청을 대기합니다.

## gRPC 서비스 정보

- **서비스명**: `AIService`
- **메서드**: `ProcessPdf`
- **포트**: 4738
