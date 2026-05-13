# aqara-rocketmq-bridge

Third-party Aqara RocketMQ bridge for Home Assistant.

This repository provides:

- a Java service that consumes Aqara RocketMQ messages;
- a Docker image for Home Assistant Container users;
- a Home Assistant add-on for Home Assistant OS users;
- a local SSE stream consumed by the `ha_aqara_devices` integration.

## Home Assistant installation shortcuts

Install the Home Assistant integration with HACS:

[![Open your Home Assistant instance and open a repository inside HACS.](https://my.home-assistant.io/badges/hacs_repository.svg)](https://my.home-assistant.io/redirect/hacs_repository/?owner=Darkdragon14&repository=ha-aqara-devices)

Add this bridge as a Home Assistant OS add-on repository:

[![Open your Home Assistant instance and add this add-on repository.](https://my.home-assistant.io/badges/supervisor_add_addon_repository.svg)](https://my.home-assistant.io/redirect/supervisor_add_addon_repository/?repository_url=https%3A%2F%2Fgithub.com%2FDarkdragon14%2Faqara-rocketmq-bridge)

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
- `GET /events` streams batched latest-state updates over SSE.

`GET /events` requires `Authorization: Bearer <BRIDGE_TOKEN>`.

## Environment variables

- `APP_ID`
- `KEY_ID`
- `APP_KEY`
- `BRIDGE_TOKEN`
- `BRIDGE_PUBLIC_URL` required canonical URL reported by `/health`
- `MQ_NAMESRV_ADDR` service default is `3rd-subscription.aqara.cn:9876`, but you should use the `MQ message subscription address` shown in Aqara `Message push`
- `ROCKETMQ_ENABLED` optional, defaults to `true`
- `BATCH_INTERVAL_MS` optional, defaults to `100`
- `SERVER_PORT` optional, defaults to `8080`

`BRIDGE_PUBLIC_URL` is required, but Aqara does not send messages to that URL. The bridge consumes RocketMQ directly, and Home Assistant uses the configured `Bridge URL` to call `/health` and `/events`.

## Local run

```bash
docker run --rm -p 8080:8080 \
  -e APP_ID=your-app-id \
  -e KEY_ID=your-key-id \
  -e APP_KEY=your-app-key \
  -e BRIDGE_TOKEN=change-me \
  -e MQ_NAMESRV_ADDR=your-message-push-nameserver \
  -e BRIDGE_PUBLIC_URL=https://bridge.example.com \
  ghcr.io/darkdragon14/aqara-rocketmq-bridge:main
```

Copy `MQ_NAMESRV_ADDR` from `MQ message subscription address` on the Aqara `Message push` page.

Replace `ghcr.io/darkdragon14/aqara-rocketmq-bridge:main` with a version tag if you publish release tags.

## Home Assistant add-on

[![Open your Home Assistant instance and add this add-on repository.](https://my.home-assistant.io/badges/supervisor_add_addon_repository.svg)](https://my.home-assistant.io/redirect/supervisor_add_addon_repository/?repository_url=https%3A%2F%2Fgithub.com%2FDarkdragon14%2Faqara-rocketmq-bridge)

The add-on uses a prebuilt GHCR image instead of relying on a local build during installation.

- add-on image reference: `ghcr.io/darkdragon14/aqara-rocketmq-bridge-addon-{arch}`
- supported add-on architectures: `amd64`, `aarch64`
- the Supervisor resolves `{arch}` and pulls the matching image for the host

When you change `addon/aqara-rocketmq-bridge/config.yaml`, keep the `version` field aligned with the published add-on image tags.

## Published images

- standard bridge container: `ghcr.io/darkdragon14/aqara-rocketmq-bridge`
- Home Assistant add-on images: `ghcr.io/darkdragon14/aqara-rocketmq-bridge-addon-{arch}`

## Cloudflare tunnel labels

If you use `ghcr.io/darkdragon14/docker-cloudflare-tunnel-sync`, expose the bridge with Docker labels on the bridge container:

```yaml
labels:
  cloudflare.tunnel.enable: "true"
  cloudflare.tunnel.hostname: example.com
  cloudflare.tunnel.service: http://aqara-rocketmq-bridge:8080
```

The tunnel sync controller reads these labels, then manages the Cloudflare tunnel ingress and DNS records from Docker metadata.

## CI/CD

- `/.github/workflows/tests.yml` runs Maven tests for the Java service
- `/.github/workflows/ghcr.yml` publishes the standard bridge image to GHCR for `amd64` and `arm64`
- `/.github/workflows/addon-checks.yml` lints and validation-builds the Home Assistant add-on
- `/.github/workflows/addon-ghcr.yml` publishes the add-on images to GHCR for `amd64` and `aarch64`

## Example SSE payload

```json
{
  "type": "snapshot",
  "cursor": 42,
  "events": [
    {
      "type": "resource_report",
      "subjectId": "lumi.xxx",
      "resourceId": "3.51.85",
      "value": "1",
      "time": 1710000000000,
      "statusCode": 0
    }
  ]
}
```

## Repository layout

- `service/` Java bridge implementation
- `docker/` container packaging and deploy examples
- `addon/` Home Assistant add-on packaging
- `docs/` architecture notes
