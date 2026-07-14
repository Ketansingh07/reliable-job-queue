package com.reliable.job.queue.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JobTest {

	@Test
	void defaultConstructor_shouldInitializeCorrectly() {
		Job job = new Job();
		assertNotNull(job.getJobId());
		assertEquals(0, job.getRetryCount());
		assertEquals(Job.Status.PENDING, job.getStatus());
		assertTrue(job.getCreatedAt() > 0);
	}

	@Test
	void parameterizedConstructor_shouldSetFieldsCorrectly() {
		Job job = new Job("EMAIL", "user@test.com", 5);
		assertNotNull(job.getJobId());
		assertEquals("EMAIL", job.getJobType());
		assertEquals("user@test.com", job.getPayload());
		assertEquals(5, job.getMaxRetries());
		assertEquals(0, job.getRetryCount());
		assertEquals(Job.Status.PENDING, job.getStatus());
	}

	@Test
	void setStatus_shouldUpdateTimestamp() {
		Job job = new Job("EMAIL", "test", 3);
		long before = job.getUpdatedAt();
		job.setStatus(Job.Status.PROCESSING);
		assertTrue(job.getUpdatedAt() >= before);
		assertEquals(Job.Status.PROCESSING, job.getStatus());
	}

	@Test
	void setters_shouldUpdateFields() {
		Job job = new Job();
		job.setJobType("REPORT_GEN");
		job.setPayload("data");
		job.setMaxRetries(3);
		job.setRetryCount(1);
		job.setErrorMessage("timeout");
		job.setNextRetryAt(5000L);

		assertEquals("REPORT_GEN", job.getJobType());
		assertEquals("data", job.getPayload());
		assertEquals(3, job.getMaxRetries());
		assertEquals(1, job.getRetryCount());
		assertEquals("timeout", job.getErrorMessage());
		assertEquals(5000L, job.getNextRetryAt());
	}
}
