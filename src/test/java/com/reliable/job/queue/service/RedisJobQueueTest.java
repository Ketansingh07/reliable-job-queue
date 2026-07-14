package com.reliable.job.queue.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.reliable.job.queue.model.Job;

@ExtendWith(MockitoExtension.class)
class RedisJobQueueTest {

	@Mock
	private RedisTemplate<String, Job> redisTemplate;

	@Mock
	private ListOperations<String, Job> listOperations;

	@Mock
	private ValueOperations<String, Job> valueOperations;

	private RedisJobQueue redisJobQueue;

	private Job testJob;

	@BeforeEach
	void setUp() {
		redisJobQueue = new RedisJobQueue(redisTemplate, 60L);
		testJob = new Job("EMAIL", "user@test.com", 3);
	}

	@Test
	void enqueue_shouldPushJobToPendingQueue() {
		when(redisTemplate.opsForList()).thenReturn(listOperations);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);

		redisJobQueue.enqueue(testJob);

		verify(listOperations).leftPush(RedisJobQueue.PENDING_QUEUE, testJob);
		assertEquals(Job.Status.PENDING, testJob.getStatus());
	}

	@Test
	void acknowledge_shouldRemoveJobFromProcessingQueue() {
		when(redisTemplate.opsForList()).thenReturn(listOperations);

		redisJobQueue.acknowledge(testJob);

		verify(listOperations).remove(RedisJobQueue.PROCESSING_QUEUE, 0, testJob);
	}

	@Test
	void markCompleted_shouldRemoveAndSetTtl() {
		when(redisTemplate.opsForList()).thenReturn(listOperations);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);

		redisJobQueue.markCompleted(testJob);

		verify(listOperations).remove(RedisJobQueue.PROCESSING_QUEUE, 0, testJob);
		verify(valueOperations).set(eq("job:status:" + testJob.getJobId()), eq(testJob), eq(60L), eq(TimeUnit.MINUTES));
		assertEquals(Job.Status.COMPLETED, testJob.getStatus());
	}

	@Test
	void moveToDeadLetter_shouldRemoveFromProcessingAndPushToDLQ() {
		when(redisTemplate.opsForList()).thenReturn(listOperations);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);

		redisJobQueue.moveToDeadLetter(testJob);

		verify(listOperations).remove(RedisJobQueue.PROCESSING_QUEUE, 0, testJob);
		verify(listOperations).leftPush(RedisJobQueue.DEAD_LETTER_QUEUE, testJob);
		assertEquals(Job.Status.DEAD, testJob.getStatus());
	}

	@Test
	void reEnqueueWithBackoff_shouldMoveBackToPending() {
		when(redisTemplate.opsForList()).thenReturn(listOperations);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);

		redisJobQueue.reEnqueueWithBackoff(testJob);

		verify(listOperations).remove(RedisJobQueue.PROCESSING_QUEUE, 0, testJob);
		verify(listOperations).leftPush(RedisJobQueue.PENDING_QUEUE, testJob);
		assertEquals(Job.Status.PENDING, testJob.getStatus());
	}

	@Test
	void getQueueStats_shouldReturnAllQueueSizes() {
		when(redisTemplate.opsForList()).thenReturn(listOperations);
		when(listOperations.size(RedisJobQueue.PENDING_QUEUE)).thenReturn(5L);
		when(listOperations.size(RedisJobQueue.PROCESSING_QUEUE)).thenReturn(2L);
		when(listOperations.size(RedisJobQueue.DEAD_LETTER_QUEUE)).thenReturn(1L);

		Map<String, Long> stats = redisJobQueue.getQueueStats();

		assertEquals(5L, stats.get("pending"));
		assertEquals(2L, stats.get("processing"));
		assertEquals(1L, stats.get("deadLetter"));
	}

	@Test
	void getDeadLetterJobs_whenEmpty_shouldReturnEmptyList() {
		when(redisTemplate.opsForList()).thenReturn(listOperations);
		when(listOperations.size(RedisJobQueue.DEAD_LETTER_QUEUE)).thenReturn(0L);

		List<Job> result = redisJobQueue.getDeadLetterJobs();

		assertTrue(result.isEmpty());
	}

	@Test
	void getJobStatus_shouldQueryRedis() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get("job:status:" + testJob.getJobId())).thenReturn(testJob);

		Job result = redisJobQueue.getJobStatus(testJob.getJobId());

		assertEquals(testJob, result);
	}

	@Test
	void retryFromDeadLetter_whenJobExists_shouldReEnqueue() {
		when(redisTemplate.opsForList()).thenReturn(listOperations);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(listOperations.size(RedisJobQueue.DEAD_LETTER_QUEUE)).thenReturn(1L);
		when(listOperations.range(RedisJobQueue.DEAD_LETTER_QUEUE, 0, 0)).thenReturn(List.of(testJob));

		boolean result = redisJobQueue.retryFromDeadLetter(testJob.getJobId());

		assertTrue(result);
	}

	@Test
	void retryFromDeadLetter_whenJobNotFound_shouldReturnFalse() {
		when(redisTemplate.opsForList()).thenReturn(listOperations);
		when(listOperations.size(RedisJobQueue.DEAD_LETTER_QUEUE)).thenReturn(1L);
		when(listOperations.range(RedisJobQueue.DEAD_LETTER_QUEUE, 0, 0)).thenReturn(List.of(testJob));

		boolean result = redisJobQueue.retryFromDeadLetter("non-existent-id");

		assertFalse(result);
	}
}
