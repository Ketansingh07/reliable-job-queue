package com.reliable.job.queue.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.reliable.job.queue.handler.JobHandler;
import com.reliable.job.queue.model.Job;

@ExtendWith(MockitoExtension.class)
class JobWorkerTest {

	@Mock
	private RedisJobQueue jobQueue;

	@Mock
	private JobHandler emailHandler;

	private JobWorker jobWorker;

	@BeforeEach
	void setUp() {
		when(emailHandler.getJobType()).thenReturn("EMAIL");
		jobWorker = new JobWorker(jobQueue, List.of(emailHandler));
	}

	@Test
	void pollAndProcess_whenQueueEmpty_shouldDoNothing() {
		when(jobQueue.dequeue()).thenReturn(null);

		jobWorker.pollAndProcess();

		verify(jobQueue, never()).markCompleted(any());
	}

	@Test
	void pollAndProcess_whenJobSucceeds_shouldMarkCompleted() throws Exception {
		Job job = new Job("EMAIL", "user@test.com", 3);
		when(jobQueue.dequeue()).thenReturn(job);

		jobWorker.pollAndProcess();

		verify(emailHandler).handle(job);
		verify(jobQueue).markCompleted(job);
	}

	@Test
	void pollAndProcess_whenHandlerThrows_shouldReEnqueueWithBackoff() throws Exception {
		Job job = new Job("EMAIL", "user@test.com", 3);
		when(jobQueue.dequeue()).thenReturn(job);
		doThrow(new RuntimeException("SMTP timeout")).when(emailHandler).handle(any());

		jobWorker.pollAndProcess();

		verify(jobQueue).reEnqueueWithBackoff(job);
	}

	@Test
	void pollAndProcess_whenRetriesExhausted_shouldMoveToDLQ() throws Exception {
		Job job = new Job("EMAIL", "user@test.com", 1);
		job.setRetryCount(1);
		when(jobQueue.dequeue()).thenReturn(job);
		doThrow(new RuntimeException("fail")).when(emailHandler).handle(any());

		jobWorker.pollAndProcess();

		verify(jobQueue).moveToDeadLetter(job);
	}
}
