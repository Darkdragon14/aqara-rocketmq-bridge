package darkdragon.aqara.bridge.web;

import darkdragon.aqara.bridge.AqaraRocketMqBridgeApplication;
import org.apache.rocketmq.client.log.ClientLogger;

public final class RocketMqLoggingProbe {

    private RocketMqLoggingProbe() {
    }

    public static void main(String[] args) throws Exception {
        Class.forName(AqaraRocketMqBridgeApplication.class.getName());
        String backend = ClientLogger.getLog().getClass().getName();
        System.out.println(backend);
        if (!backend.contains("Slf4jLoggerFactory")) {
            throw new IllegalStateException("RocketMQ did not select its SLF4J backend");
        }
    }
}
