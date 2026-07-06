# Architecture Notes

The bridge consumes Aqara RocketMQ messages, keeps the latest state per `(subjectId, resourceId)`, and streams batched updates to Home Assistant over SSE.

Home Assistant remains the owner of Aqara Open API token refresh and resource subscription setup.

This split keeps the Java bridge focused on transport reliability and avoids duplicating Aqara Open API auth logic outside the integration.

Device discovery, including hub child-device discovery through `query.device.subInfo`, stays in the Home Assistant integration. The bridge does not call Aqara Open API discovery endpoints and does not decide which Home Assistant devices or entities should exist.

## Runtime pipeline

```text
Aqara RocketMQ
-> Apache RocketMQ Java consumer
-> resource_report parser
-> in-memory latest-state store
-> batched SSE snapshot/delta stream
-> Home Assistant integration
```

## Public exposure

The service is designed to run locally on the Docker host or inside a Home Assistant add-on container.

The bridge stores a canonical address in `BRIDGE_PUBLIC_URL`, which is required and reported by `/health`. Aqara messages still arrive through RocketMQ, so the bridge does not rely on HTTP callbacks and does not manage Cloudflare directly.

`MQ_NAMESRV_ADDR` remains configurable. Aqara documentation shows `3rd-subscription.aqara.cn:9876` as an example, but the correct value is the one displayed in your own `Message push` page.

## Child devices

For hub child devices, the Home Assistant integration uses Aqara Open API discovery data to register child devices under their hub with `via_device`.

The bridge only sees resource reports after the integration has subscribed to resources. Generic child-device resources are subscribed only when the matching read-only generic entities are enabled in Home Assistant.
