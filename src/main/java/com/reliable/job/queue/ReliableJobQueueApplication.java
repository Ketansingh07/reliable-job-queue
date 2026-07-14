package com.reliable.job.queue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ReliableJobQueueApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReliableJobQueueApplication.class, args);
	}

}
