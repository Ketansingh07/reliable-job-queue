package com.reliable.job.queue;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.reliable.job.queue.model.Job;
import com.reliable.job.queue.service.RedisJobQueue;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

	private final RedisJobQueue jobQueue;

	public JobController(RedisJobQueue jobQueue) {
		this.jobQueue = jobQueue;
	}

	@PostMapping
	public ResponseEntity<Job> createJob(@RequestParam String type, @RequestParam String payload,
			@RequestParam(defaultValue = "3") int maxRetries) {

		Job job = new Job(type, payload, maxRetries);
		jobQueue.enqueue(job);
		return ResponseEntity.ok(job);
	}

	@GetMapping("/{jobId}")
	public ResponseEntity<Job> getJobStatus(@PathVariable String jobId) {
		Job job = jobQueue.getJobStatus(jobId);
		if (job == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(job);
	}

	@GetMapping("/stats")
	public ResponseEntity<Map<String, Long>> getQueueStats() {
		return ResponseEntity.ok(jobQueue.getQueueStats());
	}

	@GetMapping("/dlq")
	public ResponseEntity<List<Job>> getDeadLetterJobs() {
		return ResponseEntity.ok(jobQueue.getDeadLetterJobs());
	}

	@PostMapping("/dlq/{jobId}/retry")
	public ResponseEntity<String> retryDeadLetterJob(@PathVariable String jobId,
			@RequestParam(required = false) String redirect) {
		boolean retried = jobQueue.retryFromDeadLetter(jobId);
		if (!retried) {
			return ResponseEntity.notFound().build();
		}
		if ("dashboard".equals(redirect)) {
			return ResponseEntity.status(302).header("Location", "/dashboard").build();
		}
		return ResponseEntity.ok("Job re-queued from DLQ: " + jobId);
	}
}
