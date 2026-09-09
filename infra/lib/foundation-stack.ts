import { Duration, RemovalPolicy, Stack, StackProps } from "aws-cdk-lib";
import { PolicyStatement, Role, ServicePrincipal } from "aws-cdk-lib/aws-iam";
import { Code, Function, Runtime } from "aws-cdk-lib/aws-lambda";
import {
  InstanceClass,
  InstanceSize,
  InstanceType,
  Peer,
  Port,
  SecurityGroup,
  SubnetType,
  Vpc,
} from "aws-cdk-lib/aws-ec2";
import {
  AccountRecovery,
  Mfa,
  OAuthScope,
  ProviderAttribute,
  UserPool,
  UserPoolClient,
  UserPoolClientIdentityProvider,
  UserPoolDomain,
  UserPoolGroup,
  UserPoolIdentityProviderApple,
  UserPoolIdentityProviderGoogle,
} from "aws-cdk-lib/aws-cognito";
import * as path from "path";
import {
  Credentials,
  DatabaseInstance,
  DatabaseInstanceEngine,
  PostgresEngineVersion,
  StorageType,
  SubnetGroup,
} from "aws-cdk-lib/aws-rds";
import { BlockPublicAccess, Bucket, BucketEncryption } from "aws-cdk-lib/aws-s3";
import { Secret } from "aws-cdk-lib/aws-secretsmanager";
import { Construct } from "constructs";
import { StageConfig } from "./config";

export interface FoundationStackProps extends StackProps {
  config: StageConfig;
}

export class FoundationStack extends Stack {
  readonly vpc: Vpc;
  readonly albSecurityGroup: SecurityGroup;
  readonly ecsSecurityGroup: SecurityGroup;
  readonly rdsSecurityGroup: SecurityGroup;
  readonly bucket: Bucket;
  readonly database: DatabaseInstance;
  readonly dbSecret: Secret;
  readonly openaiSecret: Secret;
  readonly userPool: UserPool;
  readonly userPoolClient: UserPoolClient;
  readonly webUserPoolClient: UserPoolClient;
  readonly userPoolDomain: UserPoolDomain;

  constructor(scope: Construct, id: string, props: FoundationStackProps) {
    super(scope, id, props);
    const { config } = props;

    this.vpc = new Vpc(this, "Vpc", {
      maxAzs: 2,
      natGateways: 0,
      subnetConfiguration: [
        { name: "public", subnetType: SubnetType.PUBLIC, cidrMask: 24 },
        { name: "isolated", subnetType: SubnetType.PRIVATE_ISOLATED, cidrMask: 24 },
      ],
    });

    this.albSecurityGroup = new SecurityGroup(this, "AlbSg", {
      vpc: this.vpc,
      allowAllOutbound: true,
      description: "ALB internet ingress",
    });
    this.albSecurityGroup.addIngressRule(Peer.anyIpv4(), Port.tcp(443), "HTTPS");
    this.albSecurityGroup.addIngressRule(Peer.anyIpv4(), Port.tcp(80), "HTTP redirect");

    this.ecsSecurityGroup = new SecurityGroup(this, "EcsSg", {
      vpc: this.vpc,
      allowAllOutbound: true,
      description: "Fargate tasks; public IP, app port only from ALB",
    });
    this.ecsSecurityGroup.addIngressRule(this.albSecurityGroup, Port.tcp(8080), "ALB to API");

    this.rdsSecurityGroup = new SecurityGroup(this, "RdsSg", {
      vpc: this.vpc,
      allowAllOutbound: false,
      description: "RDS PostgreSQL from ECS only",
    });
    this.rdsSecurityGroup.addIngressRule(this.ecsSecurityGroup, Port.tcp(5432), "ECS to Postgres");

    this.bucket = new Bucket(this, "Artifacts", {
      blockPublicAccess: BlockPublicAccess.BLOCK_ALL,
      encryption: BucketEncryption.S3_MANAGED,
      enforceSSL: true,
      versioned: true,
      removalPolicy: RemovalPolicy.RETAIN,
    });

    this.dbSecret = new Secret(this, "DbSecret", {
      secretName: `aifishing/${config.stage}/db`,
      generateSecretString: {
        secretStringTemplate: JSON.stringify({ username: "aifishing" }),
        generateStringKey: "password",
        excludePunctuation: true,
      },
    });

    this.openaiSecret = new Secret(this, "OpenAiSecret", {
      secretName: `aifishing/${config.stage}/openai`,
      description: "Set OPENAI_API_KEY as the secret string (not committed).",
    });

    const subnetGroup = new SubnetGroup(this, "DbSubnets", {
      vpc: this.vpc,
      description: "Isolated RDS subnets",
      vpcSubnets: { subnetType: SubnetType.PRIVATE_ISOLATED },
    });

    this.database = new DatabaseInstance(this, "Postgres", {
      engine: DatabaseInstanceEngine.postgres({ version: PostgresEngineVersion.VER_16 }),
      instanceType: InstanceType.of(InstanceClass.T4G, InstanceSize.SMALL),
      vpc: this.vpc,
      vpcSubnets: { subnetType: SubnetType.PRIVATE_ISOLATED },
      subnetGroup,
      securityGroups: [this.rdsSecurityGroup],
      credentials: Credentials.fromSecret(this.dbSecret),
      allocatedStorage: 20,
      storageType: StorageType.GP3,
      storageEncrypted: true,
      multiAz: false,
      publiclyAccessible: false,
      backupRetention: Duration.days(7),
      deletionProtection: true,
      removalPolicy: RemovalPolicy.RETAIN,
      databaseName: "aifishing",
    });

    const authCode = Code.fromAsset(path.join(__dirname, "../lambda/auth"));
    const preSignUp = new Function(this, "PreSignUp", {
      runtime: Runtime.NODEJS_20_X,
      handler: "pre-signup.handler",
      code: authCode,
      timeout: Duration.seconds(10),
    });
    const defineAuthChallenge = new Function(this, "DefineAuthChallenge", {
      runtime: Runtime.NODEJS_20_X,
      handler: "define-auth-challenge.handler",
      code: authCode,
      timeout: Duration.seconds(10),
    });
    const createAuthChallenge = new Function(this, "CreateAuthChallenge", {
      runtime: Runtime.NODEJS_20_X,
      handler: "create-auth-challenge.handler",
      code: authCode,
      timeout: Duration.seconds(15),
    });
    createAuthChallenge.addToRolePolicy(
      new PolicyStatement({
        actions: ["sns:Publish"],
        resources: ["*"],
      })
    );
    const verifyAuthChallenge = new Function(this, "VerifyAuthChallenge", {
      runtime: Runtime.NODEJS_20_X,
      handler: "verify-auth-challenge.handler",
      code: authCode,
      timeout: Duration.seconds(10),
    });

    const smsExternalId = `${this.account}-${config.stage}-cognito-sms`;
    const smsRole = new Role(this, "CognitoSms", {
      assumedBy: new ServicePrincipal("cognito-idp.amazonaws.com", {
        conditions: {
          StringEquals: { "sts:ExternalId": smsExternalId },
        },
      }),
      description: "Cognito SMS via SNS. Keep the account in the SMS sandbox until destination numbers are verified.",
    });
    smsRole.addToPolicy(
      new PolicyStatement({
        actions: ["sns:Publish"],
        resources: ["*"],
      })
    );

    this.userPool = new UserPool(this, "Users", {
      userPoolName: `aifishing-${config.stage}`,
      signInAliases: { email: true, phone: true },
      autoVerify: { email: true, phone: true },
      selfSignUpEnabled: true,
      standardAttributes: {
        email: { required: false, mutable: true },
        phoneNumber: { required: false, mutable: true },
        fullname: { required: false, mutable: true },
      },
      mfa: Mfa.OPTIONAL,
      accountRecovery: AccountRecovery.EMAIL_AND_PHONE_WITHOUT_MFA,
      smsRole,
      smsRoleExternalId: smsExternalId,
      lambdaTriggers: {
        preSignUp,
        defineAuthChallenge,
        createAuthChallenge,
        verifyAuthChallengeResponse: verifyAuthChallenge,
      },
      removalPolicy: RemovalPolicy.RETAIN,
    });

    this.userPoolDomain = this.userPool.addDomain("HostedUi", {
      cognitoDomain: { domainPrefix: config.cognitoDomainPrefix },
    });

    new UserPoolGroup(this, "AdminGroup", {
      userPool: this.userPool,
      groupName: "ADMIN",
      description: "cognito:groups ADMIN maps to ROLE_ADMIN. Assign operators in the Cognito console.",
    });

    const supportedIdps = [UserPoolClientIdentityProvider.COGNITO];
    let googleIdp: UserPoolIdentityProviderGoogle | undefined;
    if (config.googleEnabled && config.googleSecretArn) {
      const googleSecret = Secret.fromSecretCompleteArn(this, "GoogleOAuth", config.googleSecretArn);
      googleIdp = new UserPoolIdentityProviderGoogle(this, "Google", {
        userPool: this.userPool,
        clientId: googleSecret.secretValueFromJson("clientId").unsafeUnwrap(),
        clientSecretValue: googleSecret.secretValueFromJson("clientSecret"),
        scopes: ["openid", "email", "profile"],
        attributeMapping: {
          email: ProviderAttribute.GOOGLE_EMAIL,
          fullname: ProviderAttribute.GOOGLE_NAME,
          profilePicture: ProviderAttribute.GOOGLE_PICTURE,
        },
      });
      supportedIdps.push(UserPoolClientIdentityProvider.GOOGLE);
    }

    let appleIdp: UserPoolIdentityProviderApple | undefined;
    if (config.appleEnabled && config.appleSecretArn) {
      const appleSecret = Secret.fromSecretCompleteArn(this, "AppleOAuth", config.appleSecretArn);
      appleIdp = new UserPoolIdentityProviderApple(this, "Apple", {
        userPool: this.userPool,
        clientId: appleSecret.secretValueFromJson("servicesId").unsafeUnwrap(),
        teamId: appleSecret.secretValueFromJson("teamId").unsafeUnwrap(),
        keyId: appleSecret.secretValueFromJson("keyId").unsafeUnwrap(),
        privateKeyValue: appleSecret.secretValueFromJson("privateKey"),
        scopes: ["name", "email"],
        attributeMapping: {
          email: ProviderAttribute.APPLE_EMAIL,
          fullname: ProviderAttribute.APPLE_NAME,
        },
      });
      supportedIdps.push(UserPoolClientIdentityProvider.APPLE);
    }

    this.userPoolClient = this.userPool.addClient("Mobile", {
      userPoolClientName: `aifishing-mobile-${config.stage}`,
      generateSecret: false,
      authFlows: { userPassword: true, userSrp: true, custom: true },
      preventUserExistenceErrors: true,
      oAuth: {
        flows: { authorizationCodeGrant: true },
        scopes: [OAuthScope.OPENID, OAuthScope.EMAIL, OAuthScope.PROFILE],
        callbackUrls: config.callbackUrls,
        logoutUrls: config.logoutUrls,
      },
      supportedIdentityProviders: supportedIdps,
      accessTokenValidity: Duration.hours(1),
      idTokenValidity: Duration.hours(1),
      refreshTokenValidity: Duration.days(30),
    });
    if (googleIdp) {
      this.userPoolClient.node.addDependency(googleIdp);
    }
    if (appleIdp) {
      this.userPoolClient.node.addDependency(appleIdp);
    }

    this.webUserPoolClient = this.userPool.addClient("Web", {
      userPoolClientName: `aifishing-web-${config.stage}`,
      generateSecret: false,
      authFlows: { userPassword: true, userSrp: true, custom: true },
      preventUserExistenceErrors: true,
      oAuth: {
        flows: { authorizationCodeGrant: true },
        scopes: [OAuthScope.OPENID, OAuthScope.EMAIL, OAuthScope.PROFILE],
        callbackUrls: config.webCallbackUrls,
        logoutUrls: config.webLogoutUrls,
      },
      supportedIdentityProviders: supportedIdps,
      accessTokenValidity: Duration.hours(1),
      idTokenValidity: Duration.hours(1),
      refreshTokenValidity: Duration.days(30),
    });
    if (googleIdp) {
      this.webUserPoolClient.node.addDependency(googleIdp);
    }
    if (appleIdp) {
      this.webUserPoolClient.node.addDependency(appleIdp);
    }
  }
}
