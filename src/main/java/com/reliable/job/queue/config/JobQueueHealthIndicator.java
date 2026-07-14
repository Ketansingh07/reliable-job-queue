package com.reliable.job.queue.config;

import java.util.Map;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.reliable.job.queue.service.RedisJobQueue;

@Component
public class JobQueueHealthIndicator implements HealthIndicator {

	private final RedisJobQueue jobQueue;

	public JobQueueHealthIndicator(RedisJobQueue jobQueue) {
		this.jobQueue = jobQueue;
	}

	@Override
	public Health health() {
		try {
			Map<String, Long> stats = jobQueue.getQueueStats();
			long dlqSize = stats.get("deadLetter");

			Health.Builder builder = dlqSize > 10 ? Health.down() : Health.up();
			return builder.withDetail("pending", stats.get("pending")).withDetail("processing", stats.get("processing"))
					.withDetail("deadLetter", dlqSize).build();
		} catch (Exception e) {
			return Health.down().withException(e).build();
		}
	}
}
