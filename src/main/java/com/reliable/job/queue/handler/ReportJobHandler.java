package com.reliable.job.queue.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.reliable.job.queue.model.Job;

@Component
public class ReportJobHandler implements JobHandler {

	private static final Logger log = LoggerFactory.getLogger(ReportJobHandler.class);

	@Override
	public String getJobType() {
		return "REPORT_GEN";
	}

	@Override
	public void handle(Job job) throws Exception {
		log.info("Generating report: {}", job.getPayload());
		Thread.sleep(200);
		log.info("Report generated successfully: {}", job.getPayload());
	}
}
