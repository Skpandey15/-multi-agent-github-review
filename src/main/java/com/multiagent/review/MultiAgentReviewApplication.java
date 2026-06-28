package com.multiagent.review;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MultiAgentReviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(MultiAgentReviewApplication.class, args);
    }
}
