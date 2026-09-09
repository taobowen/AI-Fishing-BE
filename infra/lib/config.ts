import { App } from "aws-cdk-lib";

export type StageConfig = {
  stage: string;
  region: string;
  account?: string;
  apiDomain?: string;
  hostedZoneName?: string;
  cognitoDomainPrefix: string;
  googleEnabled: boolean;
  googleSecretArn?: string;
  appleEnabled: boolean;
  appleSecretArn?: string;
  imageTag: string;
  desiredCount: number;
  cpu: number;
  memoryMiB: number;
  dbInstanceClass: string;
  logRetentionDays: number;
  callbackUrls: string[];
  logoutUrls: string[];
  webCallbackUrls: string[];
  webLogoutUrls: string[];
  webDomain?: string;
  websiteCertificateArn?: string;
};

function stringContext(app: App, key: string, fallback?: string): string | undefined {
  const value = app.node.tryGetContext(key);
  if (value === undefined || value === null || value === "") {
    return fallback;
  }
  return String(value);
}

function boolContext(app: App, key: string, fallback: boolean): boolean {
  const value = app.node.tryGetContext(key);
  if (value === undefined || value === null || value === "") {
    return fallback;
  }
  if (typeof value === "boolean") {
    return value;
  }
  return String(value).toLowerCase() === "true";
}

function numberContext(app: App, key: string, fallback: number): number {
  const value = app.node.tryGetContext(key);
  if (value === undefined || value === null || value === "") {
    return fallback;
  }
  return Number(value);
}

function listContext(app: App, key: string, fallback: string[]): string[] {
  const value = app.node.tryGetContext(key);
  if (Array.isArray(value)) {
    return value.map(String);
  }
  if (typeof value === "string" && value.length > 0) {
    return value.split(",").map((item) => item.trim()).filter(Boolean);
  }
  return fallback;
}

export function loadConfig(app: App): StageConfig {
  const googleEnabled = boolContext(app, "googleEnabled", false);
  const googleSecretArn = stringContext(app, "googleSecretArn");
  if (googleEnabled && !googleSecretArn) {
    throw new Error("googleSecretArn is required when googleEnabled=true");
  }
  const appleEnabled = boolContext(app, "appleEnabled", false);
  const appleSecretArn = stringContext(app, "appleSecretArn");
  if (appleEnabled && !appleSecretArn) {
    throw new Error("appleSecretArn is required when appleEnabled=true");
  }
  return {
    stage: stringContext(app, "stage", "prod") ?? "prod",
    region: stringContext(app, "region", "ca-central-1") ?? "ca-central-1",
    account: process.env.CDK_DEFAULT_ACCOUNT,
    apiDomain: stringContext(app, "apiDomain", "api.onwaterguide.taobowen.com"),
    hostedZoneName: stringContext(app, "hostedZoneName", "taobowen.com"),
    cognitoDomainPrefix: stringContext(app, "cognitoDomainPrefix", "castwise-taobowen") ?? "castwise-taobowen",
    googleEnabled,
    googleSecretArn,
    appleEnabled,
    appleSecretArn,
    imageTag: stringContext(app, "imageTag", "dev") ?? "dev",
    desiredCount: numberContext(app, "desiredCount", 1),
    cpu: numberContext(app, "cpu", 512),
    memoryMiB: numberContext(app, "memoryMiB", 1024),
    dbInstanceClass: stringContext(app, "dbInstanceClass", "t4g.small") ?? "t4g.small",
    logRetentionDays: numberContext(app, "logRetentionDays", 14),
    callbackUrls: listContext(app, "callbackUrls", ["aifishing://auth"]),
    logoutUrls: listContext(app, "logoutUrls", ["aifishing://auth"]),
    webCallbackUrls: listContext(app, "webCallbackUrls", [
      "http://localhost:3000/auth/callback/",
      "https://onwaterguide.taobowen.com/auth/callback/",
      "https://castwise.taobowen.com/auth/callback/",
    ]),
    webLogoutUrls: listContext(app, "webLogoutUrls", [
      "http://localhost:3000/",
      "https://onwaterguide.taobowen.com/",
      "https://castwise.taobowen.com/",
    ]),
    webDomain: stringContext(app, "webDomain", "onwaterguide.taobowen.com"),
    websiteCertificateArn: stringContext(
      app,
      "websiteCertificateArn",
      "arn:aws:acm:us-east-1:905418417106:certificate/6a1fd5ea-5b40-4040-a586-3b5bbef34b6a",
    ),
  };
}
