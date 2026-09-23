package com.aifishing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
@ConfigurationPropertiesScan
public class AiFishingApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(AiFishingApplication.class, args);
        if (context.getEnvironment().matchesProfiles("worker")) {
            System.exit(SpringApplication.exit(context));
        }
    }
}
