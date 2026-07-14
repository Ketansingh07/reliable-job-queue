package com.reliable.job.queue;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.reliable.job.queue.service.RedisJobQueue;

@Controller
public class DashboardController {

	private final RedisJobQueue jobQueue;

	public DashboardController(RedisJobQueue jobQueue) {
		this.jobQueue = jobQueue;
	}

	@GetMapping("/dashboard")
	public String dashboard(Model model) {
		model.addAttribute("stats", jobQueue.getQueueStats());
		model.addAttribute("dlqJobs", jobQueue.getDeadLetterJobs());
		return "dashboard";
	}
}
