package darkdragon.aqara.bridge;

import darkdragon.aqara.bridge.config.BridgeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(BridgeProperties.class)
public class AqaraRocketMqBridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(AqaraRocketMqBridgeApplication.class, args);
    }
}
