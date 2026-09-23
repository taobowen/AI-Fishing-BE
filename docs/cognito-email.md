# Cognito verification email

The user pool sends the verification **code** (`{####}`) using Cognito's default From address.

Custom From / domain requires a verified SES identity in the same region and `emailSendingAccount: SES` on the pool. That is not wired yet; until then Cognito's default sending account applies.

Do not add a second application email service for this flow.
