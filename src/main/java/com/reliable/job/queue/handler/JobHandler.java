package com.reliable.job.queue.handler;

import com.reliable.job.queue.model.Job;

public interface JobHandler {
	String getJobType();

	void handle(Job job) throws Exception;
}
