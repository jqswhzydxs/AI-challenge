package com.xq.algorithm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.xq.algorithm", "com.xq.infrastructure"})
@org.springframework.scheduling.annotation.EnableScheduling
public class AlgorithmApplication {
  public static void main(String[] args) {
    SpringApplication.run(AlgorithmApplication.class, args);
  }
}
