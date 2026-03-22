# ci-ngrok

Jenkins plugin for secure ngrok webhook tunneling. Allows Jenkins instances behind NAT/firewalls to receive webhooks from GitHub, GitLab, Gitea, and Bitbucket without exposing the Jenkins UI.

## How it works

1. Plugin starts an ngrok tunnel on Jenkins startup
2. Webhooks are sent to `<ngrok-url>/ci-ngrok/webhook`
3. Plugin validates the webhook signature (HMAC-SHA256 or token)
4. Valid webhooks are forwarded to the Generic Webhook Trigger plugin
5. Invalid requests are rejected with 403

Only the `/ci-ngrok/webhook` endpoint is accessible -- the Jenkins UI and API are never exposed through the tunnel.

## Supported providers

- GitHub (X-Hub-Signature-256, HMAC-SHA256)
- GitLab (X-Gitlab-Token, plain token)
- Gitea (X-Gitea-Signature, HMAC-SHA256)
- Bitbucket (X-Hub-Signature, HMAC-SHA256)

Auto-detects the provider based on which signature header is present.

## Setup

1. Install the Generic Webhook Trigger plugin
2. Install ci-ngrok.hpi via Manage Jenkins > Plugins > Advanced > Upload Plugin
3. Add your ngrok authtoken as a "Secret text" credential
4. Add your webhook secret as a "Secret text" credential
5. Go to Manage Jenkins > System > ci-ngrok Tunnel
6. Enable the tunnel and select the credentials
7. Copy the webhook URL from the ngrok Tunnel status page
8. Add it to your repository's webhook settings

## Build

```
mvn package -DskipTests
```

The `.hpi` file will be at `target/ci-ngrok.hpi`.

## Requirements

- Jenkins 2.479.3+
- Generic Webhook Trigger plugin
- ngrok account (free tier works)
