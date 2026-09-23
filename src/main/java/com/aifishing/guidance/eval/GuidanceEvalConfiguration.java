package com.aifishing.guidance.eval;

import com.aifishing.guidance.spi.EvalToolRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * EVAL beans. Does not replace the live Facade, tool registry, or DecisionPersistence.
 */
@Configuration
public class GuidanceEvalConfiguration {

    @Bean
    EvalToolRegistry evalToolRegistry(Clock clock) {
        return DefaultEvalToolRegistry.unknownOnly(clock);
    }
}
