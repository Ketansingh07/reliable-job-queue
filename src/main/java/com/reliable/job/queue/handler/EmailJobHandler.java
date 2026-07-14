package com.reliable.job.queue.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.reliable.job.queue.model.Job;

@Component
public class EmailJobHandler implements JobHandler {

	private static final Logger log = LoggerFactory.getLogger(EmailJobHandler.class);

	@Override
	public String getJobType() {
		return "EMAIL";
	}

	@Override
	public void handle(Job job) throws Exception {
		log.info("Sending email to: {}", job.getPayload());
		// Simulate email sending - replace with actual SMTP/SES call
		Thread.sleep(100);
		log.info("Email sent successfully to: {}", job.getPayload());
	}
}
