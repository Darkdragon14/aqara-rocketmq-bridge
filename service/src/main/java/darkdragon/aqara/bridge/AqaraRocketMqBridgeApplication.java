package darkdragon.aqara.bridge;

import darkdragon.aqara.bridge.config.BridgeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(BridgeProperties.class)
public class AqaraRocketMqBridgeApplication {

    static {
        // RocketMQ 4.9.8 reads this once when ClientLogger is initialized.
        System.setProperty("rocketmq.client.logUseSlf4j", "true");
    }

    public static void main(String[] args) {
        SpringApplication.run(AqaraRocketMqBridgeApplication.class, args);
    }
}
