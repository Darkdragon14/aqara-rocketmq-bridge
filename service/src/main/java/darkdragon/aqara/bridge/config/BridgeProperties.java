package darkdragon.aqara.bridge.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "bridge")
public class BridgeProperties {

    @NotBlank
    private String appId;

    @NotBlank
    private String keyId;

    @NotBlank
    private String appKey;

    @NotBlank
    private String bridgeToken;

    @NotBlank
    private String mqNamesrvAddr;

    @NotBlank
    private String bridgePublicUrl;

    private boolean rocketmqEnabled = true;

    @Positive
    private long batchIntervalMs = 100;

    @Positive
    private long heartbeatIntervalSeconds = 15;

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getBridgeToken() {
        return bridgeToken;
    }

    public void setBridgeToken(String bridgeToken) {
        this.bridgeToken = bridgeToken;
    }

    public String getMqNamesrvAddr() {
        return mqNamesrvAddr;
    }

    public void setMqNamesrvAddr(String mqNamesrvAddr) {
        this.mqNamesrvAddr = mqNamesrvAddr;
    }

    public String getBridgePublicUrl() {
        return bridgePublicUrl;
    }

    public void setBridgePublicUrl(String bridgePublicUrl) {
        this.bridgePublicUrl = bridgePublicUrl;
    }

    public boolean isRocketmqEnabled() {
        return rocketmqEnabled;
    }

    public void setRocketmqEnabled(boolean rocketmqEnabled) {
        this.rocketmqEnabled = rocketmqEnabled;
    }

    public long getHeartbeatIntervalSeconds() {
        return heartbeatIntervalSeconds;
    }

    public long getBatchIntervalMs() {
        return batchIntervalMs;
    }

    public void setBatchIntervalMs(long batchIntervalMs) {
        this.batchIntervalMs = batchIntervalMs;
    }

    public void setHeartbeatIntervalSeconds(long heartbeatIntervalSeconds) {
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
    }
}
