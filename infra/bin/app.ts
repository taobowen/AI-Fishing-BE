#!/usr/bin/env node
import { App, Environment } from "aws-cdk-lib";
import { ApiStack } from "../lib/api-stack";
import { loadConfig } from "../lib/config";
import { FoundationStack } from "../lib/foundation-stack";
import { WebsiteCertStack } from "../lib/website-cert-stack";
import { WebsiteStack } from "../lib/website-stack";

const app = new App();
const config = loadConfig(app);
const env: Environment = { account: config.account, region: config.region };
const websiteCert = config.webDomain && config.hostedZoneName
  ? new WebsiteCertStack(app, `AiFishing-WebsiteCert-${config.stage}`, {
      env: { account: config.account, region: "us-east-1" },
      crossRegionReferences: true,
      config,
    })
  : undefined;

const foundation = new FoundationStack(app, `AiFishing-Foundation-${config.stage}`, {
  env,
  config,
});

new ApiStack(app, `AiFishing-Api-${config.stage}`, {
  env,
  config,
  vpc: foundation.vpc,
  albSecurityGroup: foundation.albSecurityGroup,
  ecsSecurityGroup: foundation.ecsSecurityGroup,
  bucket: foundation.bucket,
  database: foundation.database,
  dbSecret: foundation.dbSecret,
  openaiSecret: foundation.openaiSecret,
  userPool: foundation.userPool,
  userPoolClient: foundation.userPoolClient,
  webUserPoolClient: foundation.webUserPoolClient,
  userPoolDomain: foundation.userPoolDomain,
});

const website = new WebsiteStack(app, `AiFishing-Website-${config.stage}`, {
  env,
  config,
});
if (websiteCert) {
  website.addDependency(websiteCert);
}
