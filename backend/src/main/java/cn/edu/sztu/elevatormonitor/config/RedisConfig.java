package cn.edu.sztu.elevatormonitor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置 — 为消息发布和 HSET 持久化提供正确初始化的 StringRedisTemplate。
 *
 * <p><b>修复说明</b>: 手动 new StringRedisTemplate(connectionFactory) 后必须调用
 * afterPropertiesSet() 初始化序列化器和连接，否则 HSET 操作静默失败。</p>
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        // 显式设置 String 序列化器，确保 Hash Key/Value 正确序列化
        StringRedisSerializer stringSerializer = StringRedisSerializer.UTF_8;
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);
        template.afterPropertiesSet();
        return template;
    }
}
