package com.reliable.job.queue.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.reliable.job.queue.model.Job;

@Configuration
public class RedisConfig {

	@Bean
	public RedisTemplate<String, Job> redisTemplate(RedisConnectionFactory connectionFactory) {
		RedisTemplate<String, Job> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);

		// We use Strings for Redis keys (like "queue:pending")
		StringRedisSerializer stringSerializer = new StringRedisSerializer();
		template.setKeySerializer(stringSerializer);
		template.setHashKeySerializer(stringSerializer);

		Jackson2JsonRedisSerializer<Job> jsonSerializer = new Jackson2JsonRedisSerializer<>(Job.class);
		template.setValueSerializer(jsonSerializer);
		template.setHashValueSerializer(jsonSerializer);

		template.afterPropertiesSet();
		return template;
	}
}