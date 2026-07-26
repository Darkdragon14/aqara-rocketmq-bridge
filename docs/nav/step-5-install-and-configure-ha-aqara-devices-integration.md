---
title: Step 5 - Install and Configure ha_aqara_devices Integration
parent: Setup Guide
nav_order: 8
permalink: /#step-5---install-and-configure-the-ha_aqara_devices-integration
nav_exclude: false
---

# Step 5 - Install and Configure ha_aqara_devices Integration

After the integration is installed, add `Aqara Devices` from Home Assistant and enter the Aqara Open API account, region, developer credentials, bridge URL, and bridge token.

The options flow has three sections in the camera prerelease:

| Menu | Purpose |
| --- | --- |
| `Account and bridge` | Update the Aqara account, region, developer credentials, bridge URL, or bridge token |
| `go2rtc` | Configure the optional go2rtc API and RTSP output URLs |
| `Camera streams` | Pair a supported camera through go2rtc or associate an existing go2rtc stream |

Camera streams are optional. Without go2rtc, the integration still loads all normal Open API and bridge-backed entities.

{: .warning }
> The go2rtc camera flow is available only in the experimental `v1.4.0-beta-camera-go2rtc-streams` prerelease. Stable versions do not currently include camera entities.

See [Cameras]({{ '/devices/#cameras' | relative_url }}) for go2rtc installation, HomeKit pairing, existing-stream setup, removal, security guidance, and current limitations.
