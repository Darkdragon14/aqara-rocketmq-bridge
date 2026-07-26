---
title: Cameras
parent: Supported Devices
nav_order: 1
permalink: /camera-streaming/
nav_exclude: false
---

# Cameras

The `ha_aqara_devices` integration discovers supported Aqara camera-family devices through Aqara Open API v3 and uses the bridge only for Aqara message push events.

## Experimental live streams with go2rtc

{: .warning }
> Camera streaming is currently experimental and available only in the `v1.4.0-beta-camera-go2rtc-streams` prerelease of `ha_aqara_devices`. It is not included in the current stable release. Install the prerelease only if you are comfortable testing it and reporting camera-specific issues.

G3, G2H Pro, G410, and G4 devices can expose a Home Assistant `camera` entity through a separate [go2rtc](https://github.com/AlexxIT/go2rtc) instance. This is optional: all Aqara controls, sensors, events, and bridge-backed updates continue to work without go2rtc.

The bridge does not transport camera media:

`Aqara camera -> local HomeKit connection -> go2rtc -> Home Assistant camera entity`

`aqara-rocketmq-bridge` remains responsible only for RocketMQ/SSE events. The integration uses the go2rtc API to discover and pair the camera, while Home Assistant reads the video from go2rtc's RTSP output.

## 1. Install the camera prerelease

Open the `Aqara Devices` repository in HACS, enable prerelease versions if necessary, select **Redownload**, and choose `v1.4.0-beta-camera-go2rtc-streams`. Restart Home Assistant after installation.

## 2. Install go2rtc

### Home Assistant OS or Supervised

Add the go2rtc add-on repository:

```text
https://github.com/AlexxIT/hassio-addons
```

Install and start the `go2rtc` add-on. The default endpoints are usually:

```text
API:  http://127.0.0.1:1984
RTSP: rtsp://127.0.0.1:8554
```

### Home Assistant Container

Run the official go2rtc image on the same local network as Home Assistant and the cameras. Host networking is recommended for HomeKit/mDNS discovery.

```yaml
go2rtc:
  image: alexxit/go2rtc:1.9.14
  restart: unless-stopped
  network_mode: host
  volumes:
    - ./go2rtc:/config
```

Keep `/config` persistent because go2rtc stores HomeKit pairing keys there. If Home Assistant cannot reach go2rtc through `127.0.0.1`, use the go2rtc host address in the integration options.

{: .warning }
> Do not expose the go2rtc API or RTSP ports directly to the Internet. Use localhost or a trusted private network, and configure go2rtc authentication when the API is reachable by other devices.

## 3. Connect the integration to go2rtc

In Home Assistant, open:

`Settings -> Devices & services -> Aqara Devices -> Configure -> go2rtc`

Enter:

| Field | Example | Purpose |
| --- | --- | --- |
| `go2rtc API URL` | `http://127.0.0.1:1984` | Discovery, pairing, snapshots, and stream management |
| `API username` | Optional | go2rtc HTTP Basic authentication |
| `API password` | Optional | go2rtc HTTP Basic authentication |
| `go2rtc RTSP URL` | `rtsp://127.0.0.1:8554` | Video source consumed by Home Assistant |

The integration validates the API and RTSP URL before saving. If you later need to change to another go2rtc instance, remove and unpair all streams managed by the integration first.

## 4. Pair a camera

Open:

`Settings -> Devices & services -> Aqara Devices -> Configure -> Camera streams`

1. Select the Aqara G3, G2H Pro, G410, or G4.
2. Select `Add or replace stream`.
3. Select `Pair a discovered HomeKit camera`.
4. Select the matching camera discovered by go2rtc.
5. Enter the camera's HomeKit pairing code.
6. Submit the form and wait for the integration to reload.

go2rtc performs and stores the HomeKit pairing. `ha_aqara_devices` never stores the HomeKit PIN. The Aqara app and Aqara Open API entities remain available.

HomeKit accessories generally accept one HomeKit controller at a time. If the camera is already paired with Apple Home, Home Assistant HomeKit Device, or another go2rtc instance, remove that HomeKit pairing before retrying.

## 5. Use an existing go2rtc stream

Advanced users can select `Use an existing go2rtc stream` instead of pairing. The integration lists the streams already configured in go2rtc and associates the selected stream with the Aqara device. It does not modify or delete externally managed streams.

## 6. Remove a stream

Select the camera and choose `Remove stream`. For streams paired by this integration, choose whether to remove only the Home Assistant association or also unpair the camera from go2rtc.

## Current limitations

- Automatically paired streams expose video only. HomeKit AAC-ELD audio requires an additional FFmpeg transcoding source in go2rtc.
- Two-way audio is not supported.
- Camera behavior still needs physical validation across all models, regions, and firmware variants.
- Direct RTSP settings from the earlier camera prerelease are not migrated because they cannot be converted into a HomeKit pairing.

Camera streaming is powered by [AlexxIT/go2rtc](https://github.com/AlexxIT/go2rtc), distributed separately under the MIT license. Thanks also to Aqara support for their help and information; this project is community-maintained and is not officially affiliated with or endorsed by Aqara.
