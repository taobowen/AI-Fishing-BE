# AI Fishing Web MVP

The website is a static Next.js export. Business APIs stay in Spring Boot.

## Identity

Same Cognito User Pool. Separate public app clients:

- Mobile: `aifishing-mobile-{stage}` (`APP_AUTH_CLIENT_ID`)
- Web: `aifishing-web-{stage}` (`APP_AUTH_WEB_CLIENT_ID`)

Quota is enforced from the validated JWT `client_id`, not from the URL.

Web generate requires `Idempotency-Key`. Success is consumed once per `planning_run_id`.

Production hosts:

- Site: `https://onwaterguide.taobowen.com`
- API: `https://api.onwaterguide.taobowen.com`
- Cognito: `castwise-taobowen.auth.ca-central-1.amazoncognito.com`

## Deploy

1. `cd AI-Fishing-WEB && npm run build`
2. Sync `out/` to the `WebsiteBucket` CDK output
3. Invalidate the Website CloudFront distribution
4. Set Web callback `https://{domain}/auth/callback/` on the Web app client only
5. Confirm `APP_CORS_ALLOWED_ORIGIN_PATTERNS` includes the production origin

Smoke:

- Marketing homepage
- Email / Google / Apple / SMS sign-in
- One successful Web generate consumes quota
- Same idempotency key replays
- Fourth generate is rejected
- Plan is readable from `GET /api/v1/trips/{id}/plan`
