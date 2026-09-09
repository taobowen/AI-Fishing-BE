import { CfnOutput, Duration, RemovalPolicy, Stack, StackProps } from "aws-cdk-lib";
import { Certificate, ICertificate } from "aws-cdk-lib/aws-certificatemanager";
import {
  AllowedMethods,
  CachePolicy,
  Distribution,
  Function as CloudFrontFunction,
  FunctionCode,
  FunctionEventType,
  OriginAccessIdentity,
  ViewerProtocolPolicy,
} from "aws-cdk-lib/aws-cloudfront";
import { S3Origin } from "aws-cdk-lib/aws-cloudfront-origins";
import { ARecord, HostedZone, RecordTarget } from "aws-cdk-lib/aws-route53";
import { CloudFrontTarget } from "aws-cdk-lib/aws-route53-targets";
import { BlockPublicAccess, Bucket, BucketEncryption } from "aws-cdk-lib/aws-s3";
import { Construct } from "constructs";
import { StageConfig } from "./config";

export interface WebsiteStackProps extends StackProps {
  config: StageConfig;
  certificate?: ICertificate;
}

export class WebsiteStack extends Stack {
  constructor(scope: Construct, id: string, props: WebsiteStackProps) {
    super(scope, id, props);
    const { config } = props;

    const bucket = new Bucket(this, "Site", {
      encryption: BucketEncryption.S3_MANAGED,
      blockPublicAccess: BlockPublicAccess.BLOCK_ALL,
      removalPolicy: RemovalPolicy.RETAIN,
    });

    const originAccess = new OriginAccessIdentity(this, "Oai");
    bucket.grantRead(originAccess);

    const rewrite = new CloudFrontFunction(this, "RouteIndex", {
      code: FunctionCode.fromInline(`
function handler(event) {
  var request = event.request;
  var uri = request.uri;
  if (uri === "/") {
    request.uri = "/index.html";
    return request;
  }
  if (uri.endsWith("/")) {
    request.uri = uri + "index.html";
    return request;
  }
  if (uri.indexOf(".") === -1) {
    request.uri = uri + "/index.html";
  }
  return request;
}
`),
    });

    const domain = config.webDomain;
    const zone = config.hostedZoneName && domain
      ? HostedZone.fromLookup(this, "Zone", { domainName: config.hostedZoneName })
      : undefined;
    const certificate: ICertificate | undefined = config.websiteCertificateArn
      ? Certificate.fromCertificateArn(this, "ImportedWebCert", config.websiteCertificateArn)
      : props.certificate;

    const distribution = new Distribution(this, "Cdn", {
      defaultRootObject: "index.html",
      domainNames: domain ? [domain] : undefined,
      certificate,
      defaultBehavior: {
        origin: new S3Origin(bucket, { originAccessIdentity: originAccess }),
        viewerProtocolPolicy: ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
        allowedMethods: AllowedMethods.ALLOW_GET_HEAD,
        cachePolicy: CachePolicy.CACHING_OPTIMIZED,
        functionAssociations: [
          { function: rewrite, eventType: FunctionEventType.VIEWER_REQUEST },
        ],
      },
      errorResponses: [
        {
          httpStatus: 404,
          responseHttpStatus: 404,
          responsePagePath: "/404.html",
          ttl: Duration.minutes(1),
        },
      ],
    });

    if (domain && zone) {
      new ARecord(this, "Alias", {
        zone,
        recordName: domain,
        target: RecordTarget.fromAlias(new CloudFrontTarget(distribution)),
      });
    }

    new CfnOutput(this, "WebsiteBucket", { value: bucket.bucketName });
    new CfnOutput(this, "WebsiteDistributionId", { value: distribution.distributionId });
    new CfnOutput(this, "WebsiteDomain", { value: domain ?? distribution.distributionDomainName });
  }
}
