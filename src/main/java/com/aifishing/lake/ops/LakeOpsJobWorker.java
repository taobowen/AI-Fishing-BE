package com.aifishing.lake.ops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Profile("worker")
public class LakeOpsJobWorker implements ApplicationRunner, ExitCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(LakeOpsJobWorker.class);

    private final LakeOpsProperties properties;
    private final LakeOpsJobExecutor executor;
    private volatile int exitCode = 0;

    public LakeOpsJobWorker(LakeOpsProperties properties, LakeOpsJobExecutor executor) {
        this.properties = properties;
        this.executor = executor;
    }

    @Override
    public void run(ApplicationArguments args) {
        UUID jobId = properties.getJobId();
        if (jobId == null) {
            String env = System.getenv("APP_OPS_JOB_ID");
            if (env != null && !env.isBlank()) {
                jobId = UUID.fromString(env.trim());
            }
        }
        if (jobId == null) {
            log.error("APP_OPS_JOB_ID / app.ops.jobs.job-id is required for the worker profile");
            exitCode = 2;
            return;
        }
        exitCode = executor.claimAndRun(jobId);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
