---
layout: default
title: Install shortcuts
nav_order: 2
description: Quick Home Assistant shortcuts for the Aqara integration and bridge add-on.
permalink: /INSTALL-SHORTCUTS/
has_toc: true
---

# Install shortcuts

Use these shortcuts to install the two parts of the Aqara setup:

1. the Home Assistant integration through HACS;
2. the Aqara RocketMQ Bridge add-on repository for Home Assistant OS.

## Add the Home Assistant integration

This opens your Home Assistant instance and adds the `ha-aqara-devices` custom repository in HACS.

[![Open your Home Assistant instance and open a repository inside HACS.](https://my.home-assistant.io/badges/hacs_repository.svg)](https://my.home-assistant.io/redirect/hacs_repository/?owner=Darkdragon14&repository=ha-aqara-devices)

Repository:

```text
https://github.com/Darkdragon14/ha-aqara-devices
```

## Add the bridge add-on repository

This opens your Home Assistant instance and adds the `aqara-rocketmq-bridge` add-on repository.

[![Open your Home Assistant instance and add this add-on repository.](https://my.home-assistant.io/badges/supervisor_add_addon_repository.svg)](https://my.home-assistant.io/redirect/supervisor_add_addon_repository/?repository_url=https%3A%2F%2Fgithub.com%2FDarkdragon14%2Faqara-rocketmq-bridge)

Repository:

```text
https://github.com/Darkdragon14/aqara-rocketmq-bridge
```

## Full setup guide

For the complete setup, including Aqara developer credentials, RocketMQ configuration, Docker, Home Assistant OS add-on setup, and troubleshooting, follow the full guide:

- [Aqara RocketMQ Bridge setup guide]({{ "/" | relative_url }})
