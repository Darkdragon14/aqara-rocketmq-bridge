---
title: Step 5 - Install and Configure ha_aqara_devices Integration
parent: Setup Guide
nav_order: 8
permalink: /#step-5---install-and-configure-the-ha_aqara_devices-integration
nav_exclude: false
---

# Step 5 - Install and Configure ha_aqara_devices Integration

After the integration is installed, add `Aqara Devices` from Home Assistant and enter the Aqara Open API account, region, developer credentials, bridge URL, and bridge token.

The options flow has three sections in `v1.4.0` and later:

| Menu | Purpose |
| --- | --- |
| `Account and bridge` | Update the Aqara account, region, developer credentials, bridge URL, or bridge token |
| `go2rtc` | Configure the optional go2rtc API and RTSP output URLs |
| `Camera streams` | Pair a supported camera through go2rtc or associate an existing go2rtc stream |

Camera streams are optional. Without go2rtc, the integration still loads all normal Open API and bridge-backed entities.

Supported hubs also expose Aqara child devices. The integration creates generic read-only entities from the resources reported by Aqara. Safe reportable states and common measurements are enabled automatically; writable, unknown, or less useful resources remain disabled until you enable them from the child device's entity list. Changing this setting reloads the integration so polling and subscriptions match the enabled entities.

Use `ha_aqara_devices.open_pairing_mode` or `ha_aqara_devices.close_pairing_mode` with the parent hub DID to control child-device pairing mode.

See [Cameras]({{ '/camera-streaming/' | relative_url }}) for go2rtc installation, HomeKit pairing, existing-stream setup, removal, security guidance, and current limitations.
