# Docker

This directory contains the container packaging for `aqara-rocketmq-bridge`.

## Files

- `Dockerfile` multi-stage build for the Java bridge
- `.dockerignore` keeps the build context small
- `compose.example.yaml` example deployment with bridge env vars and Cloudflare tunnel sync sidecar

## Example

```bash
docker run --rm -p 8080:8080 \
  -e APP_ID=your-app-id \
  -e KEY_ID=your-key-id \
  -e APP_KEY=your-app-key \
  -e BRIDGE_TOKEN=change-me \
  -e MQ_NAMESRV_ADDR=3rd-subscription.aqara.cn:9876 \
  -e BRIDGE_PUBLIC_URL=https://example.com \
  ghcr.io/darkdragon14/aqara-rocketmq-bridge:main
```

`BRIDGE_PUBLIC_URL` is required and should match the URL exposed through your reverse proxy or Cloudflare tunnel.

When using `ghcr.io/darkdragon14/docker-cloudflare-tunnel-sync`, add labels such as `cloudflare.tunnel.enable=true`, `cloudflare.tunnel.hostname=example.com`, and `cloudflare.tunnel.service=http://aqara-rocketmq-bridge:8080` on the bridge container.

Target registry remains `ghcr.io`.
