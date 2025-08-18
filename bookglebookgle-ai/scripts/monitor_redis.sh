#!/bin/bash

# Redis 모니터링 스크립트
# BGBG AI Server Chat History Redis 상태 확인

REDIS_PASSWORD="bgbg_redis_2024!"
REDIS_HOST="localhost"
REDIS_PORT="6379"

echo "🔍 BGBG AI Server - Redis Monitoring Dashboard"
echo "=============================================="
echo "📅 $(date)"
echo ""

# Redis 연결 상태 확인
echo "🔗 Connection Status:"
if redis-cli -h $REDIS_HOST -p $REDIS_PORT -a $REDIS_PASSWORD ping > /dev/null 2>&1; then
    echo "   ✅ Redis is running and accessible"
else
    echo "   ❌ Redis connection failed"
    exit 1
fi
echo ""

# 기본 정보
echo "📊 Basic Information:"
redis-cli -h $REDIS_HOST -p $REDIS_PORT -a $REDIS_PASSWORD info server | grep -E "(redis_version|uptime_in_seconds|uptime_in_days)" | while read line; do
    echo "   $line"
done
echo ""

# 메모리 사용량
echo "💾 Memory Usage:"
redis-cli -h $REDIS_HOST -p $REDIS_PORT -a $REDIS_PASSWORD info memory | grep -E "(used_memory_human|used_memory_peak_human|maxmemory_human|maxmemory_policy)" | while read line; do
    echo "   $line"
done
echo ""

# 클라이언트 연결
echo "👥 Client Connections:"
redis-cli -h $REDIS_HOST -p $REDIS_PORT -a $REDIS_PASSWORD info clients | grep -E "(connected_clients|client_recent_max_input_buffer|client_recent_max_output_buffer)" | while read line; do
    echo "   $line"
done
echo ""

# 키 통계
echo "🔑 Key Statistics:"
redis-cli -h $REDIS_HOST -p $REDIS_PORT -a $REDIS_PASSWORD info keyspace | while read line; do
    if [[ $line == db* ]]; then
        echo "   $line"
    fi
done

# 전체 키 개수
total_keys=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT -a $REDIS_PASSWORD dbsize)
echo "   Total keys: $total_keys"
echo ""

# 채팅 관련 키 통계
echo "💬 Chat History Keys:"
chat_session_keys=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT -a $REDIS_PASSWORD --scan --pattern "chat_session:*" | wc -l)
chat_message_keys=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT -a $REDIS_PASSWORD --scan --pattern "chat_messages:*" | wc -l)
cache_keys=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT -a $REDIS_PASSWORD --scan --pattern "cache:*" | wc -l)

echo "   Chat sessions: $chat_session_keys"
echo "   Chat messages: $chat_message_keys"
echo "   Cache entries: $cache_keys"
echo ""

# 성능 통계
echo "⚡ Performance Stats:"
redis-cli -h $REDIS_HOST -p $REDIS_PORT -a $REDIS_PASSWORD info stats | grep -E "(total_commands_processed|instantaneous_ops_per_sec|keyspace_hits|keyspace_misses)" | while read line; do
    echo "   $line"
done
echo ""

# 최근 명령어 (슬로우 로그)
echo "🐌 Recent Slow Commands:"
slow_log=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT -a $REDIS_PASSWORD slowlog get 5)
if [ -n "$slow_log" ]; then
    echo "$slow_log" | head -10
else
    echo "   No slow commands recorded"
fi
echo ""

# TTL이 있는 키들 샘플
echo "⏰ Sample Keys with TTL:"
redis-cli -h $REDIS_HOST -p $REDIS_PORT -a $REDIS_PASSWORD --scan --pattern "*" | head -5 | while read key; do
    ttl=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT -a $REDIS_PASSWORD ttl "$key")
    if [ $ttl -gt 0 ]; then
        echo "   $key: ${ttl}s remaining"
    fi
done
echo ""

# 메모리 사용률 계산 및 경고
used_memory=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT -a $REDIS_PASSWORD info memory | grep "used_memory:" | cut -d: -f2 | tr -d '\r')
max_memory=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT -a $REDIS_PASSWORD config get maxmemory | tail -1)

if [ "$max_memory" != "0" ]; then
    usage_percent=$(echo "scale=2; $used_memory * 100 / $max_memory" | bc)
    echo "🚨 Memory Usage Alert:"
    echo "   Current usage: ${usage_percent}%"
    
    if (( $(echo "$usage_percent > 80" | bc -l) )); then
        echo "   ⚠️  WARNING: Memory usage is high!"
    elif (( $(echo "$usage_percent > 90" | bc -l) )); then
        echo "   🚨 CRITICAL: Memory usage is very high!"
    else
        echo "   ✅ Memory usage is normal"
    fi
fi

echo ""
echo "=============================================="
echo "💡 Quick Commands:"
echo "   Monitor in real-time: redis-cli -a '$REDIS_PASSWORD' monitor"
echo "   Check specific key: redis-cli -a '$REDIS_PASSWORD' get <key>"
echo "   List all keys: redis-cli -a '$REDIS_PASSWORD' keys '*'"
echo "   Clear all data: redis-cli -a '$REDIS_PASSWORD' flushall"