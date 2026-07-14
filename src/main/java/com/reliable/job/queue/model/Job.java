package com.reliable.job.queue.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public class Job implements Serializable {
	private static final long serialVersionUID = 1L;

	public enum Status {
		PENDING, PROCESSING, COMPLETED, FAILED, DEAD
	}

	private String jobId;
	private String jobType;
	private String payload;
	private Status status;
	private int maxRetries;
	private int retryCount;
	private String errorMessage;
	private long createdAt;
	private long updatedAt;
	private long nextRetryAt;

	public Job() {
		this.jobId = UUID.randomUUID().toString();
		this.retryCount = 0;
		this.status = Status.PENDING;
		this.createdAt = Instant.now().toEpochMilli();
		this.updatedAt = this.createdAt;
	}

	public Job(String jobType, String payload, int maxRetries) {
		this();
		this.jobType = jobType;
		this.payload = payload;
		this.maxRetries = maxRetries;
	}

	public String getJobId() {
		return jobId;
	}

	public void setJobId(String jobId) {
		this.jobId = jobId;
	}

	public String getJobType() {
		return jobType;
	}

	public void setJobType(String jobType) {
		this.jobType = jobType;
	}

	public String getPayload() {
		return payload;
	}

	public void setPayload(String payload) {
		this.payload = payload;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
		this.updatedAt = Instant.now().toEpochMilli();
	}

	public int getMaxRetries() {
		return maxRetries;
	}

	public void setMaxRetries(int maxRetries) {
		this.maxRetries = maxRetries;
	}

	public int getRetryCount() {
		return retryCount;
	}

	public void setRetryCount(int retryCount) {
		this.retryCount = retryCount;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public long getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(long createdAt) {
		this.createdAt = createdAt;
	}

	public long getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(long updatedAt) {
		this.updatedAt = updatedAt;
	}

	public long getNextRetryAt() {
		return nextRetryAt;
	}

	public void setNextRetryAt(long nextRetryAt) {
		this.nextRetryAt = nextRetryAt;
	}

	@Override
	public String toString() {
		return "Job{jobId='" + jobId + "', jobType='" + jobType + "', status=" + status + ", retryCount=" + retryCount
				+ "}";
	}
}
