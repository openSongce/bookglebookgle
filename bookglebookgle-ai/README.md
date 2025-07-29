# BGBG (북글북글) AI Server

AI-powered features for BGBG reading platform including quiz generation, text proofreading, and discussion facilitation.

## Features

### 🎯 Quiz Generation System
- Automatic quiz generation from document content
- Progress-based triggers (50%, 100%)
- Multiple choice questions with explanations
- Korean language optimized

### ✨ AI Proofreading Assistant
- Grammar and contextual text correction
- Real-time diff generation
- Korean language grammar support
- Visual feedback with before/after comparison

### 💬 AI Discussion Facilitator
- RAG-based discussion moderation
- Real-time chat analysis
- Context-aware response generation
- Topic generation from documents

### 📊 User Analytics
- Activity pattern analysis
- Interest extraction from user behavior
- Learning insights generation
- Personalized recommendations

## Architecture

```
┌─────────────────┐    gRPC     ┌──────────────────┐
│ Spring Boot     │ <─────────> │ AI Server        │
│ Backend         │             │ (Python/FastAPI) │
└─────────────────┘             └──────────────────┘
                                         │
                                         ▼
                                ┌──────────────────┐
                                │ Vector DB        │
                                │ (ChromaDB)       │
                                └──────────────────┘
                                         │
                                         ▼
                                ┌──────────────────┐
                                │ LLM Providers    │
                                │ (OpenAI/Claude)  │
                                └──────────────────┘
```

## Quick Start

### Prerequisites

- Python 3.9+
- Node.js (for gRPC stub generation)
- Virtual environment recommended

### Installation

1. Clone the repository:
```bash
git clone <repository-url>
cd ai_part
```

2. Create virtual environment:
```bash
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
```

3. Install dependencies:
```bash
pip install -r requirements.txt
```

4. Set up environment variables:
```bash
cp .env.example .env
# Edit .env with your configuration
```

5. Generate gRPC stubs:
```bash
python generate_grpc.py
```

### Configuration

Edit `.env` file with your settings:

```env
# Server Configuration
SERVER_HOST=0.0.0.0
SERVER_PORT=50051

# AI Service Configuration
OPENAI_API_KEY=your_openai_key_here
ANTHROPIC_API_KEY=your_anthropic_key_here

# Vector Database
CHROMA_PERSIST_DIRECTORY=./data/chroma

# Feature Flags
ENABLE_QUIZ_GENERATION=true
ENABLE_PROOFREADING=true
ENABLE_DISCUSSION_AI=true
ENABLE_USER_ANALYTICS=true
```

### Running the Server

Start the AI server:
```bash
python main.py
```

The server will start on:
- gRPC: `localhost:50051`
- HTTP API: `http://localhost:50051` (for testing)

## API Documentation

### REST Endpoints (Testing)

#### Health Check
```http
GET /health
```

#### Quiz Generation
```http
POST /api/v1/test/quiz
Content-Type: application/json

{
  "document_id": "doc_123",
  "content": "Document content here...",
  "progress_percentage": 100,
  "question_count": 5,
  "difficulty_level": "medium"
}
```

#### Text Proofreading
```http
POST /api/v1/test/proofread
Content-Type: application/json

{
  "text": "Text to proofread...",
  "context": "Optional context...",
  "language": "ko",
  "user_id": "user_123"
}
```

#### Discussion Initialization
```http
POST /api/v1/test/discussion/init
Content-Type: application/json

{
  "document_id": "doc_123",
  "meeting_id": "meeting_456",
  "document_content": "Full document text...",
  "participants": [
    {"user_id": "user_1", "nickname": "User1"},
    {"user_id": "user_2", "nickname": "User2"}
  ]
}
```

### gRPC Services

The server exposes the following gRPC services defined in `protos/ai_service.proto`:

- `GenerateQuiz`: Generate quiz from document content
- `ProofreadText`: Proofread and correct text
- `InitializeDiscussion`: Initialize discussion session
- `ProcessChatMessage`: Process chat messages with AI moderation
- `GenerateDiscussionTopic`: Generate discussion topics
- `AnalyzeUserActivity`: Analyze user activity patterns
- `ExtractUserInterests`: Extract user interests from behavior

## Development

### Project Structure

```
ai_part/
├── src/
│   ├── api/                 # REST API routes
│   ├── config/             # Configuration management
│   ├── grpc_server/        # gRPC server implementation
│   ├── models/             # Pydantic models and schemas
│   ├── services/           # Core AI services
│   │   ├── vector_db.py    # Vector database management
│   │   ├── llm_client.py   # LLM provider clients
│   │   ├── quiz_service.py # Quiz generation
│   │   ├── proofreading_service.py # Text correction
│   │   ├── discussion_service.py   # Discussion AI
│   │   └── analytics_service.py    # User analytics
│   └── utils/              # Utility functions
├── protos/                 # Protocol buffer definitions
├── requirements.txt        # Python dependencies
├── main.py                # Application entry point
└── generate_grpc.py       # gRPC stub generator
```

### Adding New Features

1. Define protobuf messages in `protos/ai_service.proto`
2. Regenerate gRPC stubs: `python generate_grpc.py`
3. Implement service logic in appropriate `src/services/` module
4. Add gRPC handler in `src/grpc_server/ai_servicer.py`
5. Add REST endpoint in `src/api/routes.py` for testing
6. Update documentation

### Testing

Run individual service tests:
```bash
# Test quiz generation
curl -X POST http://localhost:50051/api/v1/test/quiz \
  -H "Content-Type: application/json" \
  -d '{"document_id": "test", "content": "테스트 문서 내용입니다.", "progress_percentage": 100}'

# Test proofreading
curl -X POST http://localhost:50051/api/v1/test/proofread \
  -H "Content-Type: application/json" \
  -d '{"text": "안녕하세요 테스트 문장입니다", "user_id": "test_user"}'
```

## Deployment

### Docker

Create `Dockerfile`:
```dockerfile
FROM python:3.9-slim

WORKDIR /app
COPY requirements.txt .
RUN pip install -r requirements.txt

COPY . .
RUN python generate_grpc.py

EXPOSE 50051
CMD ["python", "main.py"]
```

Build and run:
```bash
docker build -t bgbg-ai-server .
docker run -p 50051:50051 bgbg-ai-server
```

### Environment Variables

Required environment variables for production:
- `OPENAI_API_KEY`: OpenAI API key
- `ANTHROPIC_API_KEY`: Anthropic API key (optional)
- `MYSQL_HOST`, `MYSQL_USER`, `MYSQL_PASSWORD`: Database connection
- `REDIS_HOST`, `REDIS_PORT`: Redis for caching
- `CHROMA_PERSIST_DIRECTORY`: Vector DB storage path

## Monitoring

The server exposes metrics on port 8000 (configurable):
- Health check: `GET /health`
- Service status: `GET /status`
- Configuration: `GET /config`

## Contributing

1. Fork the repository
2. Create feature branch: `git checkout -b feature/amazing-feature`
3. Commit changes: `git commit -m 'Add amazing feature'`
4. Push to branch: `git push origin feature/amazing-feature`
5. Open pull request

## License

This project is part of the BGBG platform.

## Support

For questions and support, please refer to the development log: `AI_DEVELOPMENT_LOG.md`