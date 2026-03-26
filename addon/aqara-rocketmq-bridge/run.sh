#!/usr/bin/with-contenv bashio
set -euo pipefail

export APP_ID
APP_ID="$(bashio::config 'app_id')"

export KEY_ID
KEY_ID="$(bashio::config 'key_id')"

export APP_KEY
APP_KEY="$(bashio::config 'app_key')"

export BRIDGE_TOKEN
BRIDGE_TOKEN="$(bashio::config 'bridge_token')"

export MQ_NAMESRV_ADDR
MQ_NAMESRV_ADDR="$(bashio::config 'mq_namesrv_addr')"

export BRIDGE_PUBLIC_URL
BRIDGE_PUBLIC_URL="$(bashio::config 'bridge_public_url')"

exec java -jar /app/bridge.jar
