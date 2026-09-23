package com.aifishing.lake.ops;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@ConfigurationProperties(prefix = "app.ops.jobs")
public class LakeOpsProperties {

    /**
     * {@code inline} runs the job in-process after insert. {@code ecs} calls RunTask.
     */
    private String launcher = "inline";
    private UUID jobId;
    private Duration heartbeat = Duration.ofSeconds(30);
    private Duration launchStale = Duration.ofMinutes(5);
    private Duration runningStale = Duration.ofMinutes(15);
    private final Ecs ecs = new Ecs();

    public String getLauncher() {
        return launcher;
    }

    public void setLauncher(String launcher) {
        this.launcher = launcher;
    }

    public UUID getJobId() {
        return jobId;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    public Duration getHeartbeat() {
        return heartbeat;
    }

    public void setHeartbeat(Duration heartbeat) {
        this.heartbeat = heartbeat;
    }

    public Duration getLaunchStale() {
        return launchStale;
    }

    public void setLaunchStale(Duration launchStale) {
        this.launchStale = launchStale;
    }

    public Duration getRunningStale() {
        return runningStale;
    }

    public void setRunningStale(Duration runningStale) {
        this.runningStale = runningStale;
    }

    public Ecs getEcs() {
        return ecs;
    }

    public boolean ecsLauncher() {
        return "ecs".equalsIgnoreCase(launcher);
    }

    public static class Ecs {
        private String cluster = "";
        private String taskDefinition = "";
        private String securityGroup = "";
        private String assignPublicIp = "ENABLED";
        private String containerName = "worker";
        private String subnets = "";
        /**
         * Max in-flight ECS RunTask workers. Does not apply to {@code inline}
         * (that path is a 1-thread pool). Production must set this explicitly.
         */
        private int maxConcurrent = 2;

        public String getCluster() {
            return cluster;
        }

        public void setCluster(String cluster) {
            this.cluster = cluster;
        }

        public String getTaskDefinition() {
            return taskDefinition;
        }

        public void setTaskDefinition(String taskDefinition) {
            this.taskDefinition = taskDefinition;
        }

        public String getSecurityGroup() {
            return securityGroup;
        }

        public void setSecurityGroup(String securityGroup) {
            this.securityGroup = securityGroup;
        }

        public String getAssignPublicIp() {
            return assignPublicIp;
        }

        public void setAssignPublicIp(String assignPublicIp) {
            this.assignPublicIp = assignPublicIp;
        }

        public String getContainerName() {
            return containerName;
        }

        public void setContainerName(String containerName) {
            this.containerName = containerName;
        }

        public String getSubnets() {
            return subnets;
        }

        public void setSubnets(String subnets) {
            this.subnets = subnets == null ? "" : subnets;
        }

        public int getMaxConcurrent() {
            return maxConcurrent;
        }

        public void setMaxConcurrent(int maxConcurrent) {
            this.maxConcurrent = maxConcurrent;
        }

        public List<String> subnetIds() {
            if (subnets == null || subnets.isBlank()) {
                return List.of();
            }
            return java.util.Arrays.stream(subnets.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .toList();
        }
    }
}
