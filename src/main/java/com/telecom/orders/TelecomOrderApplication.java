package com.telecom.orders;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TelecomOrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(TelecomOrderApplication.class, args);
    }
}
