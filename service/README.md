# Service

Spring Boot service that:

- connects to Aqara RocketMQ using `APP_ID`, `KEY_ID`, and `APP_KEY`;
- consumes `resource_report` messages from the Aqara topic named after `APP_ID`;
- exposes `GET /health` and `GET /events`;
- protects `GET /events` with `BRIDGE_TOKEN`.

## Main configuration

- `APP_ID`
- `KEY_ID`
- `APP_KEY`
- `BRIDGE_TOKEN`
- `MQ_NAMESRV_ADDR`
- `BRIDGE_PUBLIC_URL`

`BRIDGE_PUBLIC_URL` is required.

## Build

Use the Docker build in `docker/Dockerfile` if Maven is not installed locally.
