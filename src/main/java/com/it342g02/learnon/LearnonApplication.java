package com.it342g02.learnon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LearnonApplication {

    public static void main(String[] args) {
        SpringApplication.run(LearnonApplication.class, args);
    }
}
