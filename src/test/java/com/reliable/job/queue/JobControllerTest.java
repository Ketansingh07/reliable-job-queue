package com.reliable.job.queue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.reliable.job.queue.model.Job;
import com.reliable.job.queue.service.RedisJobQueue;

@WebMvcTest(JobController.class)
class JobControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RedisJobQueue jobQueue;

	@Test
	void createJob_shouldReturnJobWithId() throws Exception {
		mockMvc.perform(
				post("/api/v1/jobs").param("type", "EMAIL").param("payload", "user@test.com").param("maxRetries", "3"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.jobId").exists())
				.andExpect(jsonPath("$.jobType").value("EMAIL")).andExpect(jsonPath("$.status").value("PENDING"));

		verify(jobQueue).enqueue(any(Job.class));
	}

	@Test
	void getJobStatus_whenExists_shouldReturnJob() throws Exception {
		Job job = new Job("EMAIL", "user@test.com", 3);
		when(jobQueue.getJobStatus(job.getJobId())).thenReturn(job);

		mockMvc.perform(get("/api/v1/jobs/" + job.getJobId())).andExpect(status().isOk())
				.andExpect(jsonPath("$.jobType").value("EMAIL"));
	}

	@Test
	void getJobStatus_whenNotFound_shouldReturn404() throws Exception {
		when(jobQueue.getJobStatus("unknown")).thenReturn(null);

		mockMvc.perform(get("/api/v1/jobs/unknown")).andExpect(status().isNotFound());
	}

	@Test
	void getQueueStats_shouldReturnCounts() throws Exception {
		when(jobQueue.getQueueStats()).thenReturn(Map.of("pending", 5L, "processing", 2L, "deadLetter", 1L));

		mockMvc.perform(get("/api/v1/jobs/stats")).andExpect(status().isOk()).andExpect(jsonPath("$.pending").value(5))
				.andExpect(jsonPath("$.processing").value(2)).andExpect(jsonPath("$.deadLetter").value(1));
	}

	@Test
	void getDeadLetterJobs_shouldReturnList() throws Exception {
		Job job = new Job("EMAIL", "user@test.com", 3);
		job.setStatus(Job.Status.DEAD);
		when(jobQueue.getDeadLetterJobs()).thenReturn(List.of(job));

		mockMvc.perform(get("/api/v1/jobs/dlq")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].status").value("DEAD"));
	}

	@Test
	void retryDeadLetterJob_whenExists_shouldReturnOk() throws Exception {
		when(jobQueue.retryFromDeadLetter("abc-123")).thenReturn(true);

		mockMvc.perform(post("/api/v1/jobs/dlq/abc-123/retry")).andExpect(status().isOk());
	}

	@Test
	void retryDeadLetterJob_whenNotFound_shouldReturn404() throws Exception {
		when(jobQueue.retryFromDeadLetter("unknown")).thenReturn(false);

		mockMvc.perform(post("/api/v1/jobs/dlq/unknown/retry")).andExpect(status().isNotFound());
	}
}
