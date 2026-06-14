package com.pingpongsmt;

import com.pingpongsmt.config.EnvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PingpongSmartApplication {

    public static void main(String[] args) {
        // Load .env BEFORE Spring Boot starts
        EnvLoader.load();
        SpringApplication.run(PingpongSmartApplication.class, args);
    }
}
