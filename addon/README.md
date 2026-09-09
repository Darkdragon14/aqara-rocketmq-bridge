# Home Assistant Add-on

This directory contains the Home Assistant add-on packaging for `aqara-rocketmq-bridge`.

The add-on wraps the same Java bridge used for the normal Docker image and maps add-on options to environment variables.

The experimental beta entry uses a separate slug so testers can opt in without replacing the stable add-on. Stop the stable add-on before starting the beta because both expose port `8080` by default.

Primary audience:

- Home Assistant OS users
