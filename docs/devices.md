---
layout: default
title: Supported Devices
nav_order: 3
description: Supported Aqara device families, model identifiers, and exposed Home Assistant entities.
permalink: /devices/
has_toc: false
---

# Supported devices

This page lists the Aqara devices currently supported by the `ha_aqara_devices` integration, the Aqara model identifiers matched by discovery, and the Home Assistant entities exposed for each device family.

## Cameras
{: #cameras }

### Camera Hub G3
{: #camera-hub-g3 }

Models: `lumi.camera.gwpgl1`, `lumi.camera.gwpagl01`

| Entity | Type |
| --- | --- |
| Video | `switch` |
| Detect Human | `switch` |
| Detect Pet | `switch` |
| Detect Gesture | `switch` |
| Detect Face | `switch` |
| Ring Alarm Bell | `button` |
| Up | `button` |
| Down | `button` |
| Left | `button` |
| Right | `button` |
| Night vision | `binary_sensor` |
| Motion Event | `binary_sensor` |
| Gesture V sign | `binary_sensor` |
| Gesture Four | `binary_sensor` |
| Gesture High Five | `binary_sensor` |
| Gesture Finger Gun | `binary_sensor` |
| Gesture OK | `binary_sensor` |
| Gesture V sign both hands | `binary_sensor` |
| Gesture Four both hands | `binary_sensor` |
| Gesture High Five both hands | `binary_sensor` |
| Gesture Finger Gun both hands | `binary_sensor` |
| Gesture OK both hands | `binary_sensor` |
| Volume | `number` |

### Camera Hub G2H Pro
{: #camera-hub-g2h-pro }

Models: `lumi.camera.agl001`, `lumi.camera.acn003`

| Entity | Type |
| --- | --- |
| Sound Detection | `switch` |
| Timed Sleep | `switch` |
| Motion Push | `switch` |
| Motion Detection | `switch` |
| Indicator Light | `switch` |
| Timelapse Push | `switch` |
| Sound Push | `switch` |
| Device Offline Push | `switch` |
| Motion Recording | `switch` |
| Timelapse | `switch` |
| Sound Recording | `switch` |
| Camera | `switch` |
| Restart Device | `button` |
| Restart Coordinator | `button` |
| Music Volume | `number` |
| Camera Volume | `number` |
| Alarm Volume | `number` |

## Doorbells
{: #doorbells }

### Doorbell G410
{: #doorbell-g410 }

Models: `lumi.camera.acn017`, `lumi.camera.agl006`

| Entity | Type |
| --- | --- |
| High Temperature Alarm | `switch` |
| Face Recognition Push | `switch` |
| Scheduled Sleep | `switch` |
| Indicator Light | `switch` |
| Anti-Tamper Alarm | `switch` |
| Face Recording | `switch` |
| Doorbell Notification | `switch` |
| Low Temperature Alarm | `switch` |
| Doorbell Recording | `switch` |
| Restart Device | `button` |
| Restart Coordinator | `button` |
| Doorbell Ring | `binary_sensor` |
| Alarm Status | `binary_sensor` |
| System Volume | `number` |
| Face Recognition Interval | `number` |
| Alarm Volume | `number` |
| Camera Volume | `number` |
| Battery Level | `sensor` |
| Face Recognition Event | `sensor` |
| Stranger Face Event | `sensor` |
| Alarm Ringtone | `select` |
| Screen Flip | `select` |
| Camera Mode | `select` |

### Doorbell G4
{: #doorbell-g4 }

Models: `aqara.lock.agl002`, `lumi.camera.acn005`

| Entity | Type |
| --- | --- |
| High Temperature Alarm | `switch` |
| Face Recognition Push | `switch` |
| Scheduled Sleep | `switch` |
| Anti-Tamper Alarm | `switch` |
| Face Recording | `switch` |
| Doorbell Notification | `switch` |
| Low Temperature Alarm | `switch` |
| Doorbell Recording | `switch` |
| Restart Device | `button` |
| Restart Coordinator | `button` |
| Doorbell Ring | `binary_sensor` |
| Face Recognition Interval | `number` |
| Camera Volume | `number` |
| Face Recognition Event | `sensor` |
| Stranger Face Event | `sensor` |
| Screen Flip | `select` |

## Hubs
{: #hubs }

### Hub M3
{: #hub-m3 }

Models: `lumi.gateway.acn012`, `lumi.gateway.agl004`

| Entity | Type |
| --- | --- |
| Alarm Status | `binary_sensor` |
| Device Online | `binary_sensor` |
| System Volume | `number` |
| Alarm Volume | `number` |
| Doorbell Volume | `number` |
| Alarm Duration | `number` |
| Doorbell Duration | `number` |
| Temperature | `sensor` |
| Humidity | `sensor` |
| Gateway Language | `select` |
| Alarm Ringtone | `select` |
| Doorbell Ringtone | `select` |

### Hub M100
{: #hub-m100 }

Models: `lumi.gateway.agl008`, `lumi.gateway.agl010`

| Entity | Type |
| --- | --- |
| Sub-device Deletion Protection | `switch` |
| Device Online | `binary_sensor` |
| Music Volume | `number` |
| System Volume | `number` |
| Alarm Clock Volume | `number` |
| Alarm Volume | `number` |
| Night Light Brightness | `number` |
| Arming Delay Time | `number` |
| Gateway Time Zone | `sensor` |
| Night Light Configuration | `sensor` |
| Gateway Language | `select` |
| Alarm Ringtone | `select` |
| Doorbell Ringtone | `select` |

### Hub M200
{: #hub-m200 }

Models: `lumi.gateway.agl011`

| Entity | Type |
| --- | --- |
| AC On/Off Status | `binary_sensor` |
| Alert Volume | `number` |
| System Volume | `number` |
| Alert Duration | `number` |
| Gateway Language | `select` |
| Alert Ringtone | `select` |
| AC Mode | `select` |

## Presence Sensors
{: #presence-sensors }

### Presence Sensor FP2
{: #presence-sensor-fp2 }

Models: `lumi.motion.agl001`

| Entity | Type | Notes |
| --- | --- | --- |
| Presence | `binary_sensor` |  |
| Connectivity | `binary_sensor` |  |
| Detection Area 1-30 | `binary_sensor` | created disabled by default |
| People Counting | `sensor` |  |
| People Counting (per minute) | `sensor` |  |
| Whole Area People Count (10s) | `sensor` |  |
| Zone 1-30 People Count (10s) | `sensor` | created disabled by default |
| Zone 1-7 People Count (per minute) | `sensor` | created disabled by default |
| Illuminance | `sensor` |  |
| Heart Rate | `sensor` |  |
| Respiration Rate | `sensor` |  |
| Body Movement | `sensor` |  |
| Sleep State | `sensor` |  |
| Operating Mode | `sensor` |  |
| View Zoom | `sensor` |  |
| Mounting Position | `sensor` |  |
| Installation Angle | `sensor` |  |
| Attitude Status | `sensor` |  |
| Presence Sensitivity | `sensor` |  |
| Proximity Distance | `sensor` |  |
| Fall Detection Sensitivity | `sensor` |  |
| Reverse Coordinate Direction | `sensor` |  |
| Detection Direction | `sensor` |  |
| AI Person Detection | `sensor` |  |
| Anti-light Pollution Mode | `sensor` |  |

### Presence Multi-Sensor FP300
{: #presence-multi-sensor-fp300 }

Models: `lumi.sensor_occupy.agl8`

| Entity | Type |
| --- | --- |
| Presence | `binary_sensor` |
| Motion | `binary_sensor` |
| Activity Status | `sensor` |
| Temperature | `sensor` |
| Humidity | `sensor` |
| Illuminance | `sensor` |
| Battery | `sensor` |
| Work Mode | `select` |

## Locks
{: #locks }

### Door Lock A100 Pro
{: #door-lock-a100-pro }

Models: `aqara.lock.acn001`

| Entity | Type |
| --- | --- |
| Door Event | `sensor` |
| Door Lock Status | `sensor` |
| Open Door Method | `sensor` |
| Fingerprint User ID | `sensor` |
| Password User ID | `sensor` |
| NFC User ID | `sensor` |
| HomeKit Bluetooth User ID | `sensor` |
| Temporary Password User ID | `sensor` |

### Smart Lock U200
{: #smart-lock-u200 }

Models: `aqara.matter.4447_10242`

| Entity | Type |
| --- | --- |
| Lock | `lock` |
| Reachable | `binary_sensor` |
| Battery Replacement Needed | `binary_sensor` |
| Battery | `sensor` |
| Battery Voltage | `sensor` |

### Smart Video Door Lock Xingyao
{: #smart-video-door-lock-xingyao }

Models: `aqara.lock.acn002`

| Entity | Type |
| --- | --- |
| Door Event | `sensor` |
| Door Lock Status | `sensor` |
| Open Door Method | `sensor` |
| Lock Exception Alert | `sensor` |
| Device Online Status | `sensor` |
| Zigbee Signal Strength | `sensor` |
