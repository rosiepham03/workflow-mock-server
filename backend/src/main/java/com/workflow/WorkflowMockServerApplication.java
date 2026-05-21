package com.workflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WorkflowMockServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkflowMockServerApplication.class, args);
        System.out.println("WORKFLOW MOCK SERVER IS READY FOR LOCAL TESTING");
        System.out.println("Listening on Port: " + System.getenv().getOrDefault("PORT", "8082"));
    }
}
