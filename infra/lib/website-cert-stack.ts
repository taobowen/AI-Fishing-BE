import { CfnOutput, Stack, StackProps } from "aws-cdk-lib";
import { Certificate, CertificateValidation, ICertificate } from "aws-cdk-lib/aws-certificatemanager";
import { HostedZone } from "aws-cdk-lib/aws-route53";
import { Construct } from "constructs";
import { StageConfig } from "./config";

export interface WebsiteCertStackProps extends StackProps {
  config: StageConfig;
}

/** CloudFront certificates must live in us-east-1. */
export class WebsiteCertStack extends Stack {
  readonly certificate?: ICertificate;

  constructor(scope: Construct, id: string, props: WebsiteCertStackProps) {
    super(scope, id, props);
    const { config } = props;
    if (!config.webDomain || !config.hostedZoneName) {
      return;
    }

    const zone = HostedZone.fromLookup(this, "Zone", { domainName: config.hostedZoneName });
    const altNames = config.nextWebDomain && config.nextWebDomain !== config.webDomain
      ? [config.nextWebDomain]
      : undefined;
    this.certificate = new Certificate(this, "Cert", {
      domainName: config.webDomain,
      subjectAlternativeNames: altNames,
      validation: CertificateValidation.fromDns(zone),
    });
    new CfnOutput(this, "WebsiteCertificateArn", { value: this.certificate.certificateArn });
  }
}
