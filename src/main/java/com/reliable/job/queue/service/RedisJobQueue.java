package com.reliable.job.queue.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisListCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.reliable.job.queue.model.Job;

@Service
public class RedisJobQueue {

	public static final String PENDING_QUEUE = "queue:pending";
	public static final String PROCESSING_QUEUE = "queue:processing";
	public static final String DEAD_LETTER_QUEUE = "queue:dead_letter";
	private static final String JOB_STATUS_PREFIX = "job:status:";

	private final RedisTemplate<String, Job> redisTemplate;
	private final StringRedisTemplate stringRedisTemplate;
	private final long completedJobTtlMinutes;

	public RedisJobQueue(RedisTemplate<String, Job> redisTemplate,
			StringRedisTemplate stringRedisTemplate,
			@Value("${job-queue.completed-job-ttl-minutes:60}") long completedJobTtlMinutes) {
		this.redisTemplate = redisTemplate;
		this.stringRedisTemplate = stringRedisTemplate;
		this.completedJobTtlMinutes = completedJobTtlMinutes;
	}

	public void enqueue(Job job) {
		job.setStatus(Job.Status.PENDING);
		redisTemplate.opsForList().leftPush(PENDING_QUEUE, job);
		saveJobStatus(job);
	}

	public Job dequeue() {
		return redisTemplate.execute((RedisConnection connection) -> {
			byte[] source = PENDING_QUEUE.getBytes();
			byte[] destination = PROCESSING_QUEUE.getBytes();

			byte[] rawJob = connection.listCommands().bLMove(source, destination,
					RedisListCommands.Direction.RIGHT, RedisListCommands.Direction.LEFT, 5);

			if (rawJob == null) {
				return null;
			}

			Job job = (Job) redisTemplate.getValueSerializer().deserialize(rawJob);
			if (job != null) {
				job.setStatus(Job.Status.PROCESSING);
				saveJobStatus(job);
			}
			return job;
		});
	}

	public void acknowledge(Job job) {
		// Remove from processing by finding the job with matching ID
		List<Job> processingJobs = redisTemplate.opsForList().range(PROCESSING_QUEUE, 0, -1);
		if (processingJobs != null) {
			for (Job pJob : processingJobs) {
				if (pJob.getJobId().equals(job.getJobId())) {
					redisTemplate.opsForList().remove(PROCESSING_QUEUE, 1, pJob);
					break;
				}
			}
		}
	}

	public void markCompleted(Job job) {
		acknowledge(job);
		job.setStatus(Job.Status.COMPLETED);
		saveJobStatusWithTtl(job);
		stringRedisTemplate.opsForValue().increment("stats:completed");
	}

	public void moveToDeadLetter(Job job) {
		acknowledge(job);
		job.setStatus(Job.Status.DEAD);
		redisTemplate.opsForList().leftPush(DEAD_LETTER_QUEUE, job);
		saveJobStatus(job);
	}

	public void reEnqueueWithBackoff(Job job) {
		acknowledge(job);
		job.setStatus(Job.Status.PENDING);
		redisTemplate.opsForList().leftPush(PENDING_QUEUE, job);
		saveJobStatus(job);
	}

	public Job getJobStatus(String jobId) {
		return redisTemplate.opsForValue().get(JOB_STATUS_PREFIX + jobId);
	}

	public List<Job> getDeadLetterJobs() {
		Long size = redisTemplate.opsForList().size(DEAD_LETTER_QUEUE);
		if (size == null || size == 0) {
			return List.of();
		}
		return redisTemplate.opsForList().range(DEAD_LETTER_QUEUE, 0, size - 1);
	}

	public boolean retryFromDeadLetter(String jobId) {
		List<Job> dlqJobs = getDeadLetterJobs();
		for (Job job : dlqJobs) {
			if (job.getJobId().equals(jobId)) {
				redisTemplate.opsForList().remove(DEAD_LETTER_QUEUE, 1, job);
				job.setRetryCount(0);
				job.setErrorMessage(null);
				enqueue(job);
				return true;
			}
		}
		return false;
	}

	public Map<String, Long> getQueueStats() {
		Map<String, Long> stats = new HashMap<>();
		stats.put("pending", getQueueSize(PENDING_QUEUE));
		stats.put("processing", getQueueSize(PROCESSING_QUEUE));
		stats.put("deadLetter", getQueueSize(DEAD_LETTER_QUEUE));
		String completed = stringRedisTemplate.opsForValue().get("stats:completed");
		stats.put("completed", completed != null ? Long.parseLong(completed) : 0L);
		return stats;
	}

	private Long getQueueSize(String queueName) {
		Long size = redisTemplate.opsForList().size(queueName);
		return size != null ? size : 0L;
	}

	private void saveJobStatus(Job job) {
		redisTemplate.opsForValue().set(JOB_STATUS_PREFIX + job.getJobId(), job);
	}

	private void saveJobStatusWithTtl(Job job) {
		redisTemplate.opsForValue().set(JOB_STATUS_PREFIX + job.getJobId(), job, completedJobTtlMinutes,
				TimeUnit.MINUTES);
	}
}
