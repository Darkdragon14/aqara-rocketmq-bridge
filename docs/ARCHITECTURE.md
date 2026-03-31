# Architecture Notes

The bridge consumes Aqara RocketMQ messages, keeps the latest state per `(subjectId, resourceId)`, and streams batched updates to Home Assistant over SSE.

Home Assistant remains the owner of Aqara Open API token refresh and resource subscription setup.

This split keeps the Java bridge focused on transport reliability and avoids duplicating Aqara Open API auth logic outside the integration.

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

The externally reachable address is provided through `BRIDGE_PUBLIC_URL`, which is required. The bridge does not manage Cloudflare directly and does not hardcode a domain.

`MQ_NAMESRV_ADDR` remains configurable even though Aqara documentation currently shows `3rd-subscription.aqara.cn:9876` as the documented RocketMQ nameserver example.
