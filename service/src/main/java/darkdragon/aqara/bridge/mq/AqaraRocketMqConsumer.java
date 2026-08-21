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
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.common.UtilAll;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.protocol.ResponseCode;
import org.apache.rocketmq.remoting.exception.RemotingConnectException;
import org.apache.rocketmq.remoting.exception.RemotingSendRequestException;
import org.apache.rocketmq.remoting.exception.RemotingTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeoutException;

@Component
public class AqaraRocketMqConsumer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AqaraRocketMqConsumer.class);
    private static final Duration INITIAL_RETRY_DELAY = Duration.ofSeconds(5);
    private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(5);

    private final BridgeProperties bridgeProperties;
    private final RocketMqMessageParser messageParser;
    private final EventBroadcaster eventBroadcaster;
    private final RocketMqHealth rocketMqHealth;
    private final RocketMqConsumerFactory consumerFactory;
    private final TaskScheduler taskScheduler;
    private DefaultMQPushConsumer consumer;
    private ScheduledFuture<?> retryTask;
    private int retryAttempt;
    private boolean connecting;
    private boolean shuttingDown;

    public AqaraRocketMqConsumer(
            BridgeProperties bridgeProperties,
            RocketMqMessageParser messageParser,
            EventBroadcaster eventBroadcaster,
            RocketMqHealth rocketMqHealth,
            RocketMqConsumerFactory consumerFactory,
            TaskScheduler taskScheduler
    ) {
        this.bridgeProperties = bridgeProperties;
        this.messageParser = messageParser;
        this.eventBroadcaster = eventBroadcaster;
        this.rocketMqHealth = rocketMqHealth;
        this.consumerFactory = consumerFactory;
        this.taskScheduler = taskScheduler;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!bridgeProperties.isRocketmqEnabled()) {
            LOGGER.info("RocketMQ consumer disabled by configuration");
            return;
        }

        try {
            startConsumer();
        } catch (Exception exception) {
            if (!isRetryable(exception)) {
                rocketMqHealth.markFailed(failureSummary(exception));
                LOGGER.error("RocketMQ consumer failed to start due to a non-retryable error", exception);
                throw exception;
            }
            scheduleRetry(exception);
        }
    }

    public void startConsumer() throws Exception {
        synchronized (this) {
            if (consumer != null || retryTask != null || connecting || shuttingDown) {
                return;
            }
            connecting = true;
        }

        AclClientRPCHook acl = new AclClientRPCHook(
                new SessionCredentials(bridgeProperties.getKeyId(), bridgeProperties.getAppKey())
        );

        DefaultMQPushConsumer createdConsumer = null;
        boolean accepted = false;
        Exception startupFailure = null;
        try {
            createdConsumer = consumerFactory.create(bridgeProperties.getAppId(), acl);
            createdConsumer.setVipChannelEnabled(false);
            createdConsumer.setNamesrvAddr(bridgeProperties.getMqNamesrvAddr());
            createdConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_TIMESTAMP);
            createdConsumer.setConsumeTimestamp(
                    UtilAll.timeMillisToHumanString3(Instant.now().minusSeconds(600).toEpochMilli())
            );
            createdConsumer.subscribe(bridgeProperties.getAppId(), "*");
            createdConsumer.registerMessageListener(new BridgeMessageListener());
            createdConsumer.start();
            consumerFactory.validateConnection(createdConsumer, bridgeProperties.getAppId());

            synchronized (this) {
                if (!shuttingDown) {
                    consumer = createdConsumer;
                    accepted = true;
                    retryAttempt = 0;
                    cancelRetryTask();
                    rocketMqHealth.markStarted();
                    LOGGER.info(
                            "RocketMQ consumer started for topic {} via {}",
                            bridgeProperties.getAppId(),
                            bridgeProperties.getMqNamesrvAddr()
                    );
                }
            }
        } catch (Exception exception) {
            startupFailure = exception;
            throw exception;
        } finally {
            RuntimeException cleanupFailure = null;
            if (createdConsumer != null && !accepted) {
                try {
                    consumerFactory.cleanup(createdConsumer);
                } catch (RuntimeException exception) {
                    cleanupFailure = exception;
                }
            }
            synchronized (this) {
                connecting = false;
                notifyAll();
            }
            if (cleanupFailure != null) {
                if (startupFailure != null) {
                    cleanupFailure.addSuppressed(startupFailure);
                }
                throw cleanupFailure;
            }
        }
    }

    @PreDestroy
    public void stopConsumer() {
        DefaultMQPushConsumer activeConsumer;
        boolean interrupted = false;
        synchronized (this) {
            shuttingDown = true;
            cancelRetryTask();
            while (connecting) {
                try {
                    wait();
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            activeConsumer = consumer;
            consumer = null;
        }

        try {
            if (activeConsumer != null) {
                activeConsumer.shutdown();
                LOGGER.info("RocketMQ consumer stopped");
            }
        } finally {
            rocketMqHealth.markStopped();
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void retryConsumer() {
        synchronized (this) {
            retryTask = null;
            if (shuttingDown || consumer != null) {
                return;
            }
        }

        try {
            startConsumer();
        } catch (Exception exception) {
            if (isRetryable(exception)) {
                scheduleRetry(exception);
                return;
            }

            rocketMqHealth.markFailed(failureSummary(exception));
            LOGGER.error(
                    "RocketMQ consumer failed to start due to a non-retryable error; automatic retries stopped",
                    exception
            );
        }
    }

    private synchronized void scheduleRetry(Exception exception) {
        if (shuttingDown || consumer != null || retryTask != null) {
            return;
        }

        retryAttempt++;
        Duration delay = retryDelay(retryAttempt);
        String summary = failureSummary(exception);
        rocketMqHealth.markRetrying(summary);
        LOGGER.warn(
                "RocketMQ connection to {} failed: {}. Retrying in {} seconds",
                bridgeProperties.getMqNamesrvAddr(),
                summary,
                delay.toSeconds()
        );
        LOGGER.debug("RocketMQ connection failure details", exception);
        retryTask = taskScheduler.schedule(this::retryConsumer, Instant.now().plus(delay));
    }

    private synchronized void cancelRetryTask() {
        if (retryTask != null) {
            retryTask.cancel(false);
            retryTask = null;
        }
    }

    static Duration retryDelay(int attempt) {
        long multiplier = 1L << Math.min(Math.max(attempt - 1, 0), 6);
        Duration delay = INITIAL_RETRY_DELAY.multipliedBy(multiplier);
        return delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay;
    }

    private static boolean isRetryable(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof RemotingConnectException
                    || cause instanceof RemotingSendRequestException
                    || cause instanceof RemotingTimeoutException
                    || cause instanceof IOException
                    || cause instanceof TimeoutException
                    || (cause instanceof MQBrokerException brokerException
                    && (brokerException.getResponseCode() == ResponseCode.SYSTEM_BUSY
                    || brokerException.getResponseCode() == ResponseCode.SYSTEM_ERROR))) {
                return true;
            }
        }
        return false;
    }

    private static String failureSummary(Throwable exception) {
        String summary = exception.getClass().getSimpleName();
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof MQBrokerException brokerException
                    && brokerException.getErrorMessage() != null
                    && !brokerException.getErrorMessage().isBlank()) {
                summary = brokerException.getErrorMessage();
            } else if (cause.getMessage() != null && !cause.getMessage().isBlank()) {
                summary = cause.getMessage();
            }
        }
        return summary;
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
