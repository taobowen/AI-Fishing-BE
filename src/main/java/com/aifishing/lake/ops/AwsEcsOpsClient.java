package com.aifishing.lake.ops;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.AssignPublicIp;
import software.amazon.awssdk.services.ecs.model.AwsVpcConfiguration;
import software.amazon.awssdk.services.ecs.model.ContainerOverride;
import software.amazon.awssdk.services.ecs.model.DescribeTasksRequest;
import software.amazon.awssdk.services.ecs.model.DescribeTasksResponse;
import software.amazon.awssdk.services.ecs.model.KeyValuePair;
import software.amazon.awssdk.services.ecs.model.LaunchType;
import software.amazon.awssdk.services.ecs.model.NetworkConfiguration;
import software.amazon.awssdk.services.ecs.model.RunTaskRequest;
import software.amazon.awssdk.services.ecs.model.RunTaskResponse;
import software.amazon.awssdk.services.ecs.model.Task;
import software.amazon.awssdk.services.ecs.model.TaskOverride;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.ops.jobs.launcher", havingValue = "ecs")
public class AwsEcsOpsClient implements EcsOpsClient {

    private final LakeOpsProperties properties;
    private final EcsClient ecsClient;

    public AwsEcsOpsClient(LakeOpsProperties properties) {
        this.properties = properties;
        String region = System.getenv().getOrDefault("AWS_REGION",
                System.getenv().getOrDefault("APP_AWS_REGION", "ca-central-1"));
        this.ecsClient = EcsClient.builder().region(Region.of(region)).build();
    }

    @Override
    public String runTask(UUID jobId) {
        LakeOpsProperties.Ecs ecs = properties.getEcs();
        if (ecs.getCluster().isBlank() || ecs.getTaskDefinition().isBlank() || ecs.subnetIds().isEmpty()) {
            throw new IllegalStateException("ECS worker cluster, task definition, and subnets must be configured");
        }
        AssignPublicIp publicIp = "DISABLED".equalsIgnoreCase(ecs.getAssignPublicIp())
                ? AssignPublicIp.DISABLED
                : AssignPublicIp.ENABLED;
        RunTaskResponse response = ecsClient.runTask(RunTaskRequest.builder()
                .cluster(ecs.getCluster())
                .taskDefinition(ecs.getTaskDefinition())
                .launchType(LaunchType.FARGATE)
                .count(1)
                .networkConfiguration(NetworkConfiguration.builder()
                        .awsvpcConfiguration(AwsVpcConfiguration.builder()
                                .subnets(ecs.subnetIds())
                                .securityGroups(List.of(ecs.getSecurityGroup()))
                                .assignPublicIp(publicIp)
                                .build())
                        .build())
                .overrides(TaskOverride.builder()
                        .containerOverrides(ContainerOverride.builder()
                                .name(ecs.getContainerName())
                                .environment(KeyValuePair.builder()
                                        .name("APP_OPS_JOB_ID")
                                        .value(jobId.toString())
                                        .build())
                                .build())
                        .build())
                .build());
        if (response.failures() != null && !response.failures().isEmpty()) {
            throw new IllegalStateException("ECS RunTask failed: " + response.failures().getFirst().reason());
        }
        if (response.tasks() == null || response.tasks().isEmpty() || response.tasks().getFirst().taskArn() == null) {
            throw new IllegalStateException("ECS RunTask returned no task ARN");
        }
        return response.tasks().getFirst().taskArn();
    }

    @Override
    public Optional<EcsTaskSnapshot> describe(String taskArn) {
        LakeOpsProperties.Ecs ecs = properties.getEcs();
        DescribeTasksResponse response = ecsClient.describeTasks(DescribeTasksRequest.builder()
                .cluster(ecs.getCluster())
                .tasks(taskArn)
                .build());
        if (response.tasks() == null || response.tasks().isEmpty()) {
            return Optional.empty();
        }
        Task task = response.tasks().getFirst();
        Integer exitCode = null;
        String containerReason = null;
        if (task.containers() != null && !task.containers().isEmpty()) {
            exitCode = task.containers().getFirst().exitCode();
            containerReason = task.containers().getFirst().reason();
        }
        return Optional.of(new EcsTaskSnapshot(
                task.lastStatus(),
                task.desiredStatus(),
                task.stopCodeAsString(),
                task.stoppedReason(),
                exitCode,
                containerReason
        ));
    }
}
