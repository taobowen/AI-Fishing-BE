const crypto = require("crypto");
const https = require("https");
const { URLSearchParams } = require("url");

exports.handler = async (event) => {
  const code = String(Math.floor(100000 + Math.random() * 900000));
  event.response.publicChallengeParameters = { method: "SMS_OTP" };
  event.response.privateChallengeParameters = { answer: code };
  event.response.challengeMetadata = "SMS_OTP";
  const phone = event.request.userAttributes.phone_number;
  if (phone) {
    await publishSms(phone, `AI Fishing code: ${code}`);
  }
  return event;
};

function publishSms(phone, message) {
  const region = process.env.AWS_REGION || process.env.AWS_DEFAULT_REGION || "ca-central-1";
  const host = `sns.${region}.amazonaws.com`;
  const body = new URLSearchParams({
    Action: "Publish",
    Version: "2010-03-31",
    PhoneNumber: phone,
    Message: message,
  }).toString();
  const credentials = sessionCredentials();
  const headers = signedHeaders({
    method: "POST",
    host,
    region,
    service: "sns",
    path: "/",
    body,
    credentials,
  });
  return new Promise((resolve, reject) => {
    const request = https.request(
      {
        host,
        method: "POST",
        path: "/",
        headers,
      },
      (response) => {
        const chunks = [];
        response.on("data", (chunk) => chunks.push(chunk));
        response.on("end", () => {
          if (response.statusCode && response.statusCode >= 400) {
            reject(new Error(`SNS ${response.statusCode}: ${Buffer.concat(chunks).toString()}`));
            return;
          }
          resolve();
        });
      }
    );
    request.on("error", reject);
    request.write(body);
    request.end();
  });
}

function sessionCredentials() {
  const accessKeyId = process.env.AWS_ACCESS_KEY_ID;
  const secretAccessKey = process.env.AWS_SECRET_ACCESS_KEY;
  const sessionToken = process.env.AWS_SESSION_TOKEN;
  if (!accessKeyId || !secretAccessKey) {
    throw new Error("Lambda credentials are not available for SNS");
  }
  return { accessKeyId, secretAccessKey, sessionToken };
}

function signedHeaders({ method, host, region, service, path, body, credentials }) {
  const now = new Date();
  const amzDate = now.toISOString().replace(/[:-]|\.\d{3}/g, "");
  const dateStamp = amzDate.slice(0, 8);
  const payloadHash = sha256(body);
  const extra = credentials.sessionToken ? `\nx-amz-security-token:${credentials.sessionToken}` : "";
  const signedHeaderNames = credentials.sessionToken
    ? "content-type;host;x-amz-date;x-amz-security-token"
    : "content-type;host;x-amz-date";
  const canonicalFixed = `${method}\n${path}\n\ncontent-type:application/x-www-form-urlencoded\nhost:${host}\nx-amz-date:${amzDate}${extra}\n\n${signedHeaderNames}\n${payloadHash}`;
  const credentialScope = `${dateStamp}/${region}/${service}/aws4_request`;
  const stringToSign = `AWS4-HMAC-SHA256\n${amzDate}\n${credentialScope}\n${sha256(canonicalFixed)}`;
  const signingKey = hmac(
    hmac(hmac(hmac(`AWS4${credentials.secretAccessKey}`, dateStamp), region), service),
    "aws4_request"
  );
  const signature = hmac(signingKey, stringToSign, "hex");
  const headers = {
    "Content-Type": "application/x-www-form-urlencoded",
    Host: host,
    "X-Amz-Date": amzDate,
    Authorization: `AWS4-HMAC-SHA256 Credential=${credentials.accessKeyId}/${credentialScope}, SignedHeaders=${signedHeaderNames}, Signature=${signature}`,
  };
  if (credentials.sessionToken) {
    headers["X-Amz-Security-Token"] = credentials.sessionToken;
  }
  return headers;
}

function sha256(value) {
  return crypto.createHash("sha256").update(value, "utf8").digest("hex");
}

function hmac(key, value, encoding) {
  return crypto.createHmac("sha256", key).update(value, "utf8").digest(encoding);
}
