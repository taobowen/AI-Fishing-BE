# Production deployment

Phase 8 AWS productionization. Ranking, GPS, and effort algorithms are unchanged. This document is the operator runbook; CDK lives in [`infra/`](../infra/).

## Architecture

```text
Expo app  --Bearer access JWT-->  ALB (HTTPS when domain is set)
                                      |
                                      v
                     ECS Fargate API service (512 CPU / 2048 MiB, public IP, no NAT)
                                      |
              +-----------------------+-----------------------+
              |                       |                       |
              v                       v                       v
        RDS Postgres 16          Private S3              Secrets Manager
        (Single-AZ, isolated)    raw/ derived/           DB + OPENAI_API_KEY
                                 catch-photos/
              |
              v
        Cognito User Pool + prefix domain (Hosted UI, PKCE public client)

On-demand lake workers (same image, no Service):
  POST import/process/spatial-snapshots → 202 jobId → ecs:RunTask
  Worker task definition: 1024 CPU / 4096 MiB (CDK context workerCpu / workerMemoryMiB)
  LakeWorkerSg lives in ApiStack (CI deploys it): no ALB/public ingress; RDS 5432 via ApiStack ingress; public IP (no NAT)
```

CDK is split:

| Stack | Name | Owns |
| --- | --- | --- |
| Foundation | `AiFishing-Foundation-{stage}` | VPC, RDS, S3, Cognito, secrets, API `EcsSg` → RDS 5432 |
| API | `AiFishing-Api-{stage}` | ECR, ECS API service, worker task definition, `LakeWorkerSg` + RDS 5432 ingress, ALB, alarms |
| Website cert | `AiFishing-WebsiteCert-{stage}` | CloudFront ACM certificate in **us-east-1** |
| Website | `AiFishing-Website-{stage}` | S3 + CloudFront for `anglerpilot.taobowen.com` (also `onwaterguide.taobowen.com`) |

**CDK is the only writer of the ECS TaskDefinition.** CI pushes `imageTag=$GITHUB_SHA` then `cdk deploy AiFishing-Api-{stage} -c imageTag=$SHA`. Do not `aws ecs register-task-definition` or `update-service --task-definition` in parallel.

## Prerequisites (do not invent values)

- AWS account + region (CDK context `region`, default `ca-central-1`)
- GitHub OIDC IAM role (`AWS_DEPLOY_ROLE_ARN`) scoped to this repo — **not** AdministratorAccess
- GitHub Actions variables: `AWS_REGION`, `ECR_REPOSITORY` (from ApiStack output after first foundation+api synth/deploy), optional `CDK_STAGE`
- **Locked hostnames** (Route53 zone `taobowen.com`):
  - Website: `https://anglerpilot.taobowen.com`
  - API: `https://api.onwaterguide.taobowen.com`
  - Cognito Hosted UI: `https://castwise-taobowen.auth.ca-central-1.amazoncognito.com`
- If `apiDomain` / `hostedZoneName` are unset, the ALB stays HTTP :80. That is a **production blocker**.
- Cognito prefix (`cognitoDomainPrefix`, default `castwise-taobowen`) must be globally unique
- Google / Apple IdP only when `googleEnabled` / `appleEnabled` are true **and** the matching Secrets Manager ARN is supplied. Native **email** sign-up/sign-in is always available.
- OpenAI API key in the `openai` secret CDK creates
- Production Mapbox public access token for the app (`EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN`). Preview/production EAS fails at config time if it is missing. See [`../AI-Fishing-FE/docs/map-platform.md`](../../AI-Fishing-FE/docs/map-platform.md)
- RDS master user must be able to `CREATE EXTENSION postgis` — Flyway [`V1__enable_postgis.sql`](../src/main/resources/db/migration/V1__enable_postgis.sql) is the source of truth (no parameter-group toggle)

## Bootstrap

```bash
cd infra
npm install
npx cdk bootstrap aws://$ACCOUNT/$REGION
npx cdk bootstrap aws://$ACCOUNT/us-east-1
npx cdk deploy AiFishing-Foundation-prod
# Google and Apple IdPs are enabled in cdk.json
# (secrets castwise/prod/google-oauth and castwise/prod/apple-signin).
npx cdk deploy AiFishing-WebsiteCert-prod AiFishing-Website-prod
```

Put `OPENAI_API_KEY` into the OpenAI secret. Record the User Pool domain, app client id, ECR URI, and ALB DNS from stack outputs.

First image (must be **linux/amd64** — Fargate worker/API tasks are `X86_64`. An Apple Silicon `docker build` without the pin is not pullable):

```bash
SHA=$(git rev-parse HEAD)
docker build --platform linux/amd64 --provenance=false -t $ECR_URI:$SHA ..
docker push $ECR_URI:$SHA
npx cdk deploy AiFishing-Api-prod -c imageTag=$SHA
```

If `docker push` hangs or drops the connection (common on Docker Desktop), retry with [crane](https://github.com/google/go-containerregistry/blob/main/cmd/crane/README.md):

```bash
docker save $ECR_URI:$SHA | crane push /dev/stdin $ECR_URI:$SHA
```

After deploy, confirm the **worker** task definition digest matches the image you pushed, then enqueue one IMPORT and watch CloudWatch (`streamPrefix` `lake-worker`) for Spring start. The worker must not log `APPLICATION FAILED TO START` (the `prod,worker` + `web-none` HttpSecurity crash).

Subsequent deploys are the GitHub Actions `deploy` job: `mvn test` → `docker build --platform linux/amd64 --provenance=false` → ECR tag git SHA → `cdk deploy AiFishing-Api-$STAGE -c imageTag=$SHA` → `aws ecs wait services-stable` (ALB target health). ECS circuit breaker rolls back a failed task. Do not run a parallel `aws ecs update-service`.

## Cognito and identity

- Hosted UI: `https://castwise-taobowen.auth.ca-central-1.amazoncognito.com`
- Two public PKCE clients, scopes `openid email profile`:
  - Mobile callbacks: `aifishing://auth`
  - Web callbacks: `https://onwaterguide.taobowen.com/auth/callback/`, `https://castwise.taobowen.com/auth/callback/` (cutover), and `http://localhost:3000/auth/callback/`
- Google Cloud **Web** client (Cognito talks to Google; the website does not):
  - Authorized JavaScript origins: `https://castwise-taobowen.auth.ca-central-1.amazoncognito.com`
  - Authorized redirect URIs: `https://castwise-taobowen.auth.ca-central-1.amazoncognito.com/oauth2/idpresponse`
- Apple Services ID return URL is the same Cognito `/oauth2/idpresponse` URI. Domains and subdomains: `castwise-taobowen.auth.ca-central-1.amazoncognito.com`
- API accepts **access tokens only** (`token_use=access`, issuer, signature, exp, `client_id` matching the app client). ID tokens are rejected. `X-User-Id` is ignored in `prod`.
- `cognito:groups` containing `ADMIN` → `ROLE_ADMIN`. CDK creates an empty `ADMIN` group; assign users in the Cognito console.
- JIT user: unique `(auth_provider, auth_subject)`. Email is unique and **not** a merge key. Same email + different subject → **409 `IDENTITY_EMAIL_CONFLICT`**. Account linking is future work.

## App / storage

- `SPRING_PROFILES_ACTIVE=prod`, Hikari max 5, Flyway on, `ddl-auto=validate`, seed off, swagger off
- API task: **512 CPU / 2048 MiB**. Lake import/process/snapshot workers: **1024 CPU / 4096 MiB**, launched with `ecs:RunTask` (not a second Service). `app.ops.jobs.ecs.max-concurrent` (prod default **2**) caps in-flight RunTask workers. Inline's 1-thread pool does **not** apply when `launcher=ecs`. `LakeWorkerSg` and its RDS 5432 ingress live in **ApiStack**, so the usual CI `cdk deploy AiFishing-Api-*` applies them — do not wait on a Foundation redeploy for worker DB access. Container Insights is off; CloudWatch Logs stay (`streamPrefix` `api` / `lake-worker`).
- `app.ops.jobs.launcher=ecs` on the API. Workers use `prod,worker` (web-none, Flyway off, Hikari 2) and claim `APP_OPS_JOB_ID`. Images are `linux/amd64`.
- `app.raw.storage=s3` — lake ingest, derived artifacts, and catch photos share the private bucket
- Catch photo `complete()` **HEADs** the server-owned key and persists actual size/type. IAM is CDK `grantReadWrite` (`s3:GetObject` covers HEAD). There is no `s3:HeadObject` action.
- Photo failure does not void the catch

## Data bootstrap (after a clean RDS)

1. Flyway runs on ECS start (`CREATE EXTENSION postgis` then V2…V16)
2. Assign the operator to Cognito group `ADMIN` (CDK creates the empty group)
3. `POST /api/v1/admin/lakes/validation-catalog` then the script in [`docs/production-data-bootstrap.md`](production-data-bootstrap.md): live `import` + GIS `process` for Head, Rice, Scugog, Simcoe. **Do not** skip import just because a dataset is `AVAILABLE`. Admin import/process/spatial-snapshots return **202** with a `jobId`; poll `GET /api/v1/admin/lakes/jobs/{jobId}` until `SUCCEEDED` **and** structured fields (`identityResolved`+`ogfId` for IMPORT; `spatialSnapshotStatus=READY`+id for GIS/HYBRID PROCESS). Gate on `status` + `failureCode` + those fields, never `errorMessage` text. GET also reconciles STOPPED ECS tasks. Generate Plan stays on the always-on API. A `LAKE_IDS` subset writes `docs/reports/lake-ops-{env}-{runId}.md` and must not overwrite `production-data-validation.md`.
4. Users generate their own strategy+plan (`POST /api/v1/trips/{id}/plan` with an empty body). Admin strategy endpoints remain for debug. No prod seed/reset.

ALB idle timeout stays **15 minutes**. Do not drop it in this cutover; measure a representative Simcoe Generate Plan first. Worker CPU/memory knobs are CDK context; do not shrink the API below 2 GB or worker CPU to 512 without measurements.

## Rollback, backup, cost

- Rollback: redeploy the previous git SHA as `imageTag` via CDK. Circuit breaker also rolls back a bad new task.
- Backup: RDS 7-day automated backups, deletion protection, RETAIN + final snapshot. S3 versioning on. Catch/effort rows are empirical history — back them up with PostGIS.
- **MVP always-on cost:** ALB + 1 Fargate **API** task (512/2048) + Single-AZ small RDS + S3 + CloudWatch + Cognito + ECR. Import/process/snapshot are on-demand RunTask workers (LakeWorkerSg, public IP). **No NAT**, Redis, RDS Proxy, Multi-AZ, SQS, or EventBridge. Public-IP ECS is a cost-conscious MVP choice, not a hardened private-subnet design.

## Smoke (API)

- `GET /actuator/health` through the ALB
- Hosted UI sign-in (email) returns an **access** token
- Access token `GET /api/v1/trips` 200; no token 401; ID token 401; `X-User-Id` alone 401
- Admin lake import/process as `ROLE_ADMIN`; `ROLE_USER` on `/api/v1/admin/**` is 403
- User Generate Plan on an owned trip with READY lake data (no prior strategy row)
- Catch photo upload → PUT → complete (HEAD size/type) → GET URL; a bad type/size stays PENDING and does not void the catch

## Unverified in this workspace

No AWS account, device, Google secret, custom domain, or map token was available here. Treat as **not verified**:

- Real Cognito Hosted UI + Google IdP
- ACM / HTTPS listener and DNS
- ECS task reaching RDS (SG + public IP routing) and S3/OpenAI
- Flyway `CREATE EXTENSION` on the chosen RDS master user
- EAS production/preview builds, Cognito redirect on a physical device
- Background GPS + photo copy on iOS/Android (Expo Go is insufficient)
- Production Mapbox tiles (requires `EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN` on the EAS profile)

Device smoke requires a development or production build, not Expo Go. See the FE README.
