# aqara-rocketmq-bridge

Third-party Aqara RocketMQ bridge for Home Assistant.

This repository provides:

- a Java service that consumes Aqara RocketMQ messages;
- a Docker image for Home Assistant Container users;
- a Home Assistant add-on for Home Assistant OS users;
- a local SSE stream consumed by the `ha_aqara_devices` integration.

## Runtime flow

```text
Aqara RocketMQ -> aqara-rocketmq-bridge -> SSE -> Home Assistant integration
```

The Home Assistant integration remains responsible for Aqara Open API authentication and `config.resource.subscribe` calls.

## Aqara developer setup

You need an Aqara developer project before running the bridge.

1. Create an account on `https://developer.aqara.com/` and log in.
2. Open the console and create a new project in `Project Management`.
3. Wait until the project is approved.
4. Copy the project `appId` from the project details page and use it as `APP_ID`.
5. Open `Project Details -> Key Management` and copy the generated `Key ID` and `App Key` as `KEY_ID` and `APP_KEY`.
6. Open `Project Details -> Message Push Settings`, choose `Message Queue Push`, select the same `Key ID`, and enable the push configuration.

## Aqara region notes

- Aqara Open API endpoints are regional, for example `open-cn.aqara.com`, `open-usa.aqara.com`, `open-ger.aqara.com`, `open-sg.aqara.com`, `open-kr.aqara.com`, and `open-ru.aqara.com`.
- Aqara documentation currently shows `3rd-subscription.aqara.cn:9876` as the RocketMQ nameserver example for message queue push.
- This repository keeps `MQ_NAMESRV_ADDR` configurable because Aqara may provide another MQ endpoint depending on your server area or future platform changes.
- If Aqara support gives you a different RocketMQ address for your project region, use that value in `MQ_NAMESRV_ADDR`.

## Endpoints

- `GET /health` returns bridge and RocketMQ status.
- `GET /events` streams normalized `resource_report` messages over SSE.

`GET /events` requires `Authorization: Bearer <BRIDGE_TOKEN>`.

## Environment variables

- `APP_ID`
- `KEY_ID`
- `APP_KEY`
- `BRIDGE_TOKEN`
- `BRIDGE_PUBLIC_URL` required public URL exposed through Cloudflare Tunnel or another reverse proxy
- `MQ_NAMESRV_ADDR` defaults to `3rd-subscription.aqara.cn:9876`
- `ROCKETMQ_ENABLED` optional, defaults to `true`
- `SERVER_PORT` optional, defaults to `8080`

`BRIDGE_PUBLIC_URL` is required because the bridge is meant to be addressed through a stable external URL such as `https://example.com`.

## Local run

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

Replace `ghcr.io/darkdragon14/aqara-rocketmq-bridge:main` with a version tag if you publish release tags.

## Cloudflare tunnel labels

If you use `ghcr.io/darkdragon14/docker-cloudflare-tunnel-sync`, expose the bridge with Docker labels on the bridge container:

```yaml
labels:
  cloudflare.tunnel.enable: "true"
  cloudflare.tunnel.hostname: example.com
  cloudflare.tunnel.service: http://aqara-rocketmq-bridge:8080
```

The tunnel sync controller reads these labels, then manages the Cloudflare tunnel ingress and DNS records from Docker metadata.

## Example SSE payload

```json
{
  "type": "resource_report",
  "subjectId": "lumi.xxx",
  "resourceId": "3.51.85",
  "value": "1",
  "time": 1710000000000,
  "statusCode": 0
}
```

## Repository layout

- `service/` Java bridge implementation
- `docker/` container packaging and deploy examples
- `addon/` Home Assistant add-on packaging
- `docs/` architecture notes
