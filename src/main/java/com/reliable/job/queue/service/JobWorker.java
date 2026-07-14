package com.reliable.job.queue.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.reliable.job.queue.handler.JobHandler;
import com.reliable.job.queue.model.Job;

@Component
public class JobWorker {

	private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

	private final RedisJobQueue jobQueue;
	private final Map<String, JobHandler> handlerRegistry;

	public JobWorker(RedisJobQueue jobQueue, List<JobHandler> handlers) {
		this.jobQueue = jobQueue;
		this.handlerRegistry = handlers.stream().collect(Collectors.toMap(JobHandler::getJobType, Function.identity()));
	}

	@Scheduled(fixedDelay = 1000)
	public void pollAndProcess() {
		Job job;
		try {
			job = jobQueue.dequeue();
		} catch (Exception e) {
			log.warn("Redis connection issue, will retry: {}", e.getMessage());
			return;
		}
		if (job == null) {
			return;
		}

		// Exponential backoff: skip if not ready for retry yet
		if (job.getNextRetryAt() > 0 && Instant.now().toEpochMilli() < job.getNextRetryAt()) {
			jobQueue.reEnqueueWithBackoff(job);
			return;
		}

		try {
			processJob(job);
			jobQueue.markCompleted(job);
			log.info("Job completed: {}", job);
		} catch (Exception e) {
			handleFailure(job, e);
		}
	}

	private void processJob(Job job) throws Exception {
		JobHandler handler = handlerRegistry.get(job.getJobType());
		if (handler == null) {
			throw new IllegalArgumentException("No handler registered for job type: " + job.getJobType());
		}
		handler.handle(job);
	}

	private void handleFailure(Job job, Exception e) {
		job.setRetryCount(job.getRetryCount() + 1);
		job.setErrorMessage(e.getMessage());

		if (job.getRetryCount() >= job.getMaxRetries()) {
			log.error("Job exhausted retries, moving to DLQ: {}", job);
			jobQueue.moveToDeadLetter(job);
		} else {
			// Exponential backoff: 2^retryCount seconds (2s, 4s, 8s, ...)
			long backoffMs = (long) Math.pow(2, job.getRetryCount()) * 1000;
			job.setNextRetryAt(Instant.now().toEpochMilli() + backoffMs);
			log.warn("Job failed (attempt {}/{}), retrying in {}ms: {}", job.getRetryCount(), job.getMaxRetries(),
					backoffMs, job);
			jobQueue.reEnqueueWithBackoff(job);
		}
	}
}
