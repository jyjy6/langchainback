package langhchainback.langchain.Redis;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.List;

// ChatMemoryStore 구현체 (예시 – 실제 LangChain4j에서 제공되지 않을 수도 있으므로 직접 구현 필요)
public class RedisChatMemoryStore implements ChatMemoryStore {

    private final RedisTemplate<String, Object> redisTemplate;
    private final String prefix = "chat:memory:";

    public RedisChatMemoryStore(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }


    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = prefix + memoryId;
        // Redis에서 리스트 또는 JSON 형태로 읽기
        return (List<ChatMessage>) redisTemplate.opsForValue().get(key);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = prefix + memoryId;
        redisTemplate.opsForValue().set(key, messages);
        // TTL(만료시간) 설정 가능
        redisTemplate.expire(key, Duration.ofHours(1));
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String key = prefix + memoryId;
        redisTemplate.delete(key);
    }
}

