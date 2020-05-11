package com.maxisvest.fabric;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Create by yuyang
 * 2020/5/8 17:56
 */
@SpringBootApplication
@EnableAsync
public class Application extends SpringBootServletInitializer {

    public static void main(String[] args) {

        SpringApplication.run(Application.class, args);
    }
}
