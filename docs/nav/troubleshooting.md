---
title: Troubleshooting
parent: Setup Guide
nav_order: 10
permalink: /#troubleshooting
nav_exclude: false
---

# Troubleshooting

## Camera stream does not appear

Home Assistant creates an Aqara `camera` entity only when all of these are true:

- `Aqara Devices` `v1.4.0` or later is installed.
- The device is discovered as a supported G3, G2H Pro, G410, or G4 model.
- The integration is loaded, so the `Camera streams` options page can read the discovered devices.
- A compatible go2rtc API and RTSP URL are configured.
- The camera is paired through go2rtc or associated with an existing go2rtc stream.

## Camera stream opens but video or snapshots fail

The bridge is not involved in video delivery. Check the go2rtc WebUI and verify that the configured stream can produce a snapshot.

Common causes:

- go2rtc cannot discover the camera because mDNS does not cross the network or VLAN boundary.
- The camera is already paired with another HomeKit controller.
- The HomeKit PIN is incorrect.
- Home Assistant cannot reach the configured go2rtc API or RTSP address.
- go2rtc was restarted without persistent `/config`, losing the pairing keys.
- The selected existing stream was renamed or removed.

Automatically paired streams currently expose video only. For audio, configure AAC/Opus transcoding in go2rtc and associate the resulting existing stream.

## A child device or entity does not appear

- Confirm that the parent is a supported G3, G2H Pro, M3, M100, or M200 hub and that the child appears in Aqara Home.
- Reload the integration after adding a child device or closing pairing mode.
- Open the child device's entity list. Writable, unknown, and less useful resources are disabled by default and must be enabled manually.
- Enabling or disabling a child entity reloads the integration so Aqara polling and subscriptions include only active resources.
