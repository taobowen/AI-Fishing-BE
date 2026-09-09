import { CfnOutput, Duration, Stack, StackProps } from "aws-cdk-lib";
import { Certificate, CertificateValidation } from "aws-cdk-lib/aws-certificatemanager";
import { Alarm, ComparisonOperator, TreatMissingData } from "aws-cdk-lib/aws-cloudwatch";
import { SecurityGroup, SubnetType, Vpc } from "aws-cdk-lib/aws-ec2";
import { Repository } from "aws-cdk-lib/aws-ecr";
import {
  Cluster,
  ContainerImage,
  CpuArchitecture,
  FargateService,
  FargateTaskDefinition,
  LogDrivers,
  OperatingSystemFamily,
  Protocol,
  Secret as EcsSecret,
} from "aws-cdk-lib/aws-ecs";
import {
  ApplicationLoadBalancer,
  ApplicationProtocol,
  ApplicationTargetGroup,
  HttpCodeTarget,
  ListenerAction,
  SslPolicy,
  TargetType,
} from "aws-cdk-lib/aws-elasticloadbalancingv2";
import { UserPool, UserPoolClient, UserPoolDomain } from "aws-cdk-lib/aws-cognito";
import { RetentionDays } from "aws-cdk-lib/aws-logs";
import { DatabaseInstance } from "aws-cdk-lib/aws-rds";
import { ARecord, HostedZone, RecordTarget } from "aws-cdk-lib/aws-route53";
import { LoadBalancerTarget } from "aws-cdk-lib/aws-route53-targets";
import { Bucket } from "aws-cdk-lib/aws-s3";
import { Secret } from "aws-cdk-lib/aws-secretsmanager";
import { Construct } from "constructs";
import { StageConfig } from "./config";

export interface ApiStackProps extends StackProps {
  config: StageConfig;
  vpc: Vpc;
  albSecurityGroup: SecurityGroup;
  ecsSecurityGroup: SecurityGroup;
  bucket: Bucket;
  database: DatabaseInstance;
  dbSecret: Secret;
  openaiSecret: Secret;
  userPool: UserPool;
  userPoolClient: UserPoolClient;
  webUserPoolClient: UserPoolClient;
  userPoolDomain: UserPoolDomain;
}

function retention(days: number): RetentionDays {
  if (days <= 14) {
    return RetentionDays.TWO_WEEKS;
  }
  if (days <= 30) {
    return RetentionDays.ONE_MONTH;
  }
  return RetentionDays.TWO_MONTHS;
}

export class ApiStack extends Stack {
  constructor(scope: Construct, id: string, props: ApiStackProps) {
    super(scope, id, props);
    const { config } = props;

    const repositoryName = `aifishing-api-${config.stage}`;
    const repository = Repository.fromRepositoryName(this, "ApiRepo", repositoryName);

    const cluster = new Cluster(this, "Cluster", { vpc: props.vpc, containerInsights: true });

    const taskDefinition = new FargateTaskDefinition(this, "Task", {
      cpu: config.cpu,
      memoryLimitMiB: config.memoryMiB,
      runtimePlatform: {
        cpuArchitecture: CpuArchitecture.X86_64,
        operatingSystemFamily: OperatingSystemFamily.LINUX,
      },
    });
    props.bucket.grantReadWrite(taskDefinition.taskRole);

    const issuer = `https://cognito-idp.${this.region}.amazonaws.com/${props.userPool.userPoolId}`;
    const jdbcHost = props.database.instanceEndpoint.hostname;
    const container = taskDefinition.addContainer("api", {
      image: ContainerImage.fromEcrRepository(repository, config.imageTag),
      logging: LogDrivers.awsLogs({
        streamPrefix: "api",
        logRetention: retention(config.logRetentionDays),
      }),
      environment: {
        SPRING_PROFILES_ACTIVE: "prod",
        SERVER_PORT: "8080",
        APP_RAW_STORAGE: "s3",
        AWS_REGION: this.region,
        AWS_DEFAULT_REGION: this.region,
        APP_AWS_REGION: this.region,
        APP_S3_BUCKET: props.bucket.bucketName,
        APP_AUTH_ISSUER: issuer,
        APP_AUTH_CLIENT_ID: props.userPoolClient.userPoolClientId,
        APP_AUTH_WEB_CLIENT_ID: props.webUserPoolClient.userPoolClientId,
        APP_CORS_ALLOWED_ORIGIN_PATTERNS: [
          "http://localhost:*",
          "http://127.0.0.1:*",
          ...(config.webDomain ? [`https://${config.webDomain}`] : []),
        ].join(","),
        SPRING_DATASOURCE_URL: `jdbc:postgresql://${jdbcHost}:5432/aifishing`,
        JAVA_TOOL_OPTIONS: "-XX:MaxRAMPercentage=75 -XX:+UseG1GC",
      },
      secrets: {
        SPRING_DATASOURCE_USERNAME: EcsSecret.fromSecretsManager(props.dbSecret, "username"),
        SPRING_DATASOURCE_PASSWORD: EcsSecret.fromSecretsManager(props.dbSecret, "password"),
        OPENAI_API_KEY: EcsSecret.fromSecretsManager(props.openaiSecret),
      },
    });
    container.addPortMappings({ containerPort: 8080, protocol: Protocol.TCP });

    const service = new FargateService(this, "Service", {
      cluster,
      taskDefinition,
      desiredCount: config.desiredCount,
      assignPublicIp: true,
      vpcSubnets: { subnetType: SubnetType.PUBLIC },
      securityGroups: [props.ecsSecurityGroup],
      circuitBreaker: { rollback: true },
      healthCheckGracePeriod: Duration.minutes(5),
      minHealthyPercent: 100,
      maxHealthyPercent: 200,
    });

    const alb = new ApplicationLoadBalancer(this, "Alb", {
      vpc: props.vpc,
      internetFacing: true,
      securityGroup: props.albSecurityGroup,
      idleTimeout: Duration.minutes(15),
    });

    const targetGroup = new ApplicationTargetGroup(this, "ApiTargets", {
      vpc: props.vpc,
      port: 8080,
      protocol: ApplicationProtocol.HTTP,
      targetType: TargetType.IP,
      healthCheck: {
        path: "/actuator/health",
        healthyHttpCodes: "200",
        interval: Duration.seconds(30),
        timeout: Duration.seconds(10),
        healthyThresholdCount: 2,
        unhealthyThresholdCount: 5,
      },
      deregistrationDelay: Duration.seconds(30),
    });
    service.attachToApplicationTargetGroup(targetGroup);

    const httpsReady = Boolean(config.apiDomain && config.hostedZoneName);
    if (httpsReady) {
      const zone = HostedZone.fromLookup(this, "Zone", { domainName: config.hostedZoneName! });
      const cert = new Certificate(this, "Cert", {
        domainName: config.apiDomain!,
        validation: CertificateValidation.fromDns(zone),
      });
      alb.addListener("Https", {
        port: 443,
        protocol: ApplicationProtocol.HTTPS,
        certificates: [cert],
        sslPolicy: SslPolicy.RECOMMENDED_TLS,
        defaultTargetGroups: [targetGroup],
      });
      alb.addListener("HttpRedirect", {
        port: 80,
        protocol: ApplicationProtocol.HTTP,
        defaultAction: ListenerAction.redirect({ protocol: "HTTPS", port: "443", permanent: true }),
      });
      new ARecord(this, "ApiDns", {
        zone,
        recordName: config.apiDomain,
        target: RecordTarget.fromAlias(new LoadBalancerTarget(alb)),
      });
    } else {
      alb.addListener("HttpBootstrap", {
        port: 80,
        protocol: ApplicationProtocol.HTTP,
        defaultTargetGroups: [targetGroup],
      });
    }

    new Alarm(this, "Target5xx", {
      metric: targetGroup.metrics.httpCodeTarget(HttpCodeTarget.TARGET_5XX_COUNT, { period: Duration.minutes(5) }),
      threshold: 5,
      evaluationPeriods: 2,
      treatMissingData: TreatMissingData.NOT_BREACHING,
    });
    new Alarm(this, "UnhealthyHosts", {
      metric: targetGroup.metrics.unhealthyHostCount(),
      threshold: 1,
      evaluationPeriods: 2,
      treatMissingData: TreatMissingData.NOT_BREACHING,
    });
    new Alarm(this, "EcsCpu", {
      metric: service.metricCpuUtilization(),
      threshold: 80,
      evaluationPeriods: 3,
      treatMissingData: TreatMissingData.NOT_BREACHING,
    });
    new Alarm(this, "EcsMemory", {
      metric: service.metricMemoryUtilization(),
      threshold: 85,
      evaluationPeriods: 3,
      treatMissingData: TreatMissingData.NOT_BREACHING,
    });
    new Alarm(this, "RdsCpu", {
      metric: props.database.metricCPUUtilization(),
      threshold: 80,
      evaluationPeriods: 3,
      treatMissingData: TreatMissingData.NOT_BREACHING,
    });
    new Alarm(this, "RdsStorage", {
      metric: props.database.metricFreeStorageSpace(),
      threshold: 2 * 1024 * 1024 * 1024,
      evaluationPeriods: 2,
      comparisonOperator: ComparisonOperator.LESS_THAN_THRESHOLD,
      treatMissingData: TreatMissingData.NOT_BREACHING,
    });
    new Alarm(this, "RdsConnections", {
      metric: props.database.metricDatabaseConnections(),
      threshold: 80,
      evaluationPeriods: 2,
      treatMissingData: TreatMissingData.NOT_BREACHING,
    });

    new CfnOutput(this, "RepositoryUri", { value: repository.repositoryUri });
    new CfnOutput(this, "UserPoolId", { value: props.userPool.userPoolId });
    new CfnOutput(this, "UserPoolClientId", { value: props.userPoolClient.userPoolClientId });
    new CfnOutput(this, "UserPoolWebClientId", { value: props.webUserPoolClient.userPoolClientId });
    new CfnOutput(this, "CognitoDomain", {
      value: `${props.userPoolDomain.domainName}.auth.${this.region}.amazoncognito.com`,
    });
    new CfnOutput(this, "AlbDns", { value: alb.loadBalancerDnsName });
    new CfnOutput(this, "BucketName", { value: props.bucket.bucketName });
    new CfnOutput(this, "HttpsConfigured", { value: String(httpsReady) });
    new CfnOutput(this, "ClusterName", { value: cluster.clusterName });
    new CfnOutput(this, "ServiceName", { value: service.serviceName });
  }
}
