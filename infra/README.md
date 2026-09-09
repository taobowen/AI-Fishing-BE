# AWS CDK (v2 TypeScript)

Cost-conscious MVP: public ECS tasks (`assignPublicIp`), isolated RDS, **no NAT Gateway**. Production HTTPS needs `apiDomain` + `hostedZoneName`. See [docs/production-deployment.md](../docs/production-deployment.md).

```bash
cd infra
npm install
npx cdk synth
# after pushing image $SHA to ECR:
npx cdk deploy AiFishing-Api-prod -c imageTag=$SHA
```
