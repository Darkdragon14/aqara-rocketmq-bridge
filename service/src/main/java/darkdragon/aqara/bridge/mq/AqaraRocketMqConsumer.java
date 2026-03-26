package darkdragon.aqara.bridge.mq;

import darkdragon.aqara.bridge.config.BridgeProperties;
import darkdragon.aqara.bridge.model.AqaraEvent;
import darkdragon.aqara.bridge.stream.EventBroadcaster;
import jakarta.annotation.PreDestroy;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.rebalance.AllocateMessageQueueAveragely;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.UtilAll;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Component
public class AqaraRocketMqConsumer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AqaraRocketMqConsumer.class);

    private final BridgeProperties bridgeProperties;
    private final RocketMqMessageParser messageParser;
    private final EventBroadcaster eventBroadcaster;
    private final RocketMqHealth rocketMqHealth;
    private DefaultMQPushConsumer consumer;

    public AqaraRocketMqConsumer(
            BridgeProperties bridgeProperties,
            RocketMqMessageParser messageParser,
            EventBroadcaster eventBroadcaster,
            RocketMqHealth rocketMqHealth
    ) {
        this.bridgeProperties = bridgeProperties;
        this.messageParser = messageParser;
        this.eventBroadcaster = eventBroadcaster;
        this.rocketMqHealth = rocketMqHealth;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!bridgeProperties.isRocketmqEnabled()) {
            LOGGER.info("RocketMQ consumer disabled by configuration");
            return;
        }

        startConsumer();
    }

    public synchronized void startConsumer() throws MQClientException {
        if (consumer != null) {
            return;
        }

        AclClientRPCHook acl = new AclClientRPCHook(
                new SessionCredentials(bridgeProperties.getKeyId(), bridgeProperties.getAppKey())
        );

        DefaultMQPushConsumer createdConsumer = new DefaultMQPushConsumer(
                bridgeProperties.getAppId(),
                acl,
                new AllocateMessageQueueAveragely()
        );
        createdConsumer.setVipChannelEnabled(false);
        createdConsumer.setNamesrvAddr(bridgeProperties.getMqNamesrvAddr());
        createdConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_TIMESTAMP);
        createdConsumer.setConsumeTimestamp(
                UtilAll.timeMillisToHumanString3(Instant.now().minusSeconds(600).toEpochMilli())
        );
        createdConsumer.subscribe(bridgeProperties.getAppId(), "*");
        createdConsumer.registerMessageListener(new BridgeMessageListener());
        createdConsumer.start();

        consumer = createdConsumer;
        rocketMqHealth.markStarted();
        LOGGER.info(
                "RocketMQ consumer started for topic {} via {}",
                bridgeProperties.getAppId(),
                bridgeProperties.getMqNamesrvAddr()
        );
    }

    @PreDestroy
    public synchronized void stopConsumer() {
        if (consumer == null) {
            return;
        }

        consumer.shutdown();
        consumer = null;
        rocketMqHealth.markStopped();
        LOGGER.info("RocketMQ consumer stopped");
    }

    private class BridgeMessageListener implements MessageListenerConcurrently {

        @Override
        public ConsumeConcurrentlyStatus consumeMessage(
                List<MessageExt> messages,
                ConsumeConcurrentlyContext context
        ) {
            for (MessageExt message : messages) {
                try {
                    String payload = new String(message.getBody(), StandardCharsets.UTF_8);
                    List<AqaraEvent> events = messageParser.parse(payload);
                    for (AqaraEvent event : events) {
                        eventBroadcaster.publish(event);
                    }
                    if (!events.isEmpty()) {
                        rocketMqHealth.markMessageReceived();
                    }
                } catch (Exception exception) {
                    rocketMqHealth.markError(exception);
                    LOGGER.warn("Failed to process RocketMQ message", exception);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }

            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }
    }
}
