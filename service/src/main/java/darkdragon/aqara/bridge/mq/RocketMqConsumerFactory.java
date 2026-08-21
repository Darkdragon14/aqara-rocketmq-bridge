package darkdragon.aqara.bridge.mq;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.rebalance.AllocateMessageQueueAveragely;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.impl.MQClientManager;
import org.apache.rocketmq.client.impl.consumer.DefaultMQPushConsumerImpl;
import org.apache.rocketmq.client.impl.factory.MQClientInstance;
import org.apache.rocketmq.common.ServiceState;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.protocol.ResponseCode;
import org.apache.rocketmq.common.protocol.heartbeat.ConsumerData;
import org.apache.rocketmq.common.protocol.heartbeat.ConsumeType;
import org.apache.rocketmq.common.protocol.heartbeat.HeartbeatData;
import org.apache.rocketmq.common.protocol.route.BrokerData;
import org.apache.rocketmq.common.protocol.route.TopicRouteData;
import org.apache.rocketmq.remoting.RPCHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
class RocketMqConsumerFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(RocketMqConsumerFactory.class);

    DefaultMQPushConsumer create(String consumerGroup, RPCHook rpcHook) {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(
                consumerGroup,
                rpcHook,
                new AllocateMessageQueueAveragely()
        );
        consumer.setInstanceName("aqara-bridge-" + UUID.randomUUID());
        return consumer;
    }

    void validateConnection(DefaultMQPushConsumer consumer, String topic) throws Exception {
        try {
            validateConnectionInterruptibly(consumer, topic);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw exception;
        }
    }

    private void validateConnectionInterruptibly(DefaultMQPushConsumer consumer, String topic) throws Exception {
        DefaultMQPushConsumerImpl implementation = consumer.getDefaultMQPushConsumerImpl();
        MQClientInstance client = implementation.getmQClientFactory();
        if (client == null) {
            throw new IllegalStateException("RocketMQ client factory was not initialized");
        }

        TopicRouteData route = client.getMQClientAPIImpl().getTopicRouteInfoFromNameServer(
                topic,
                consumer.getMqClientApiTimeout()
        );
        List<String> brokerAddresses = brokerAddresses(route);
        if (brokerAddresses.isEmpty()) {
            throw new MQClientException("No broker available for topic " + topic, null);
        }

        ConsumerData consumerData = new ConsumerData();
        consumerData.setGroupName(consumer.getConsumerGroup());
        consumerData.setConsumeType(ConsumeType.CONSUME_PASSIVELY);
        consumerData.setMessageModel(consumer.getMessageModel());
        consumerData.setConsumeFromWhere(consumer.getConsumeFromWhere());
        consumerData.getSubscriptionDataSet().addAll(implementation.getSubscriptionInner().values());

        HeartbeatData heartbeat = new HeartbeatData();
        heartbeat.setClientID(client.getClientId());
        heartbeat.getConsumerDataSet().add(consumerData);
        List<Exception> failures = new ArrayList<>();
        MQBrokerException permanentFailure = null;
        for (String brokerAddress : brokerAddresses) {
            try {
                client.getMQClientAPIImpl().sendHeartbeat(
                        brokerAddress,
                        heartbeat,
                        consumer.getMqClientApiTimeout()
                );
                return;
            } catch (InterruptedException exception) {
                throw exception;
            } catch (Exception exception) {
                failures.add(exception);
                if (exception instanceof MQBrokerException brokerException
                        && brokerException.getResponseCode() != ResponseCode.SYSTEM_BUSY
                        && brokerException.getResponseCode() != ResponseCode.SYSTEM_ERROR
                        && permanentFailure == null) {
                    permanentFailure = brokerException;
                }
            }
        }

        Exception failure = permanentFailure != null ? permanentFailure : failures.get(0);
        failures.stream()
                .filter(candidate -> candidate != failure)
                .forEach(failure::addSuppressed);
        throw failure;
    }

    private List<String> brokerAddresses(TopicRouteData route) {
        Set<String> addresses = new LinkedHashSet<>();
        if (route == null || route.getBrokerDatas() == null) {
            return List.of();
        }

        for (BrokerData broker : route.getBrokerDatas()) {
            if (broker == null || broker.getBrokerAddrs() == null) {
                continue;
            }
            Map<Long, String> brokerAddresses = broker.getBrokerAddrs();
            addBrokerAddress(addresses, brokerAddresses.get(MixAll.MASTER_ID));
            brokerAddresses.entrySet().stream()
                    .filter(entry -> entry != null && entry.getKey() != null)
                    .filter(entry -> entry.getKey() != MixAll.MASTER_ID)
                    .sorted(Map.Entry.comparingByKey())
                    .map(Map.Entry::getValue)
                    .forEach(address -> addBrokerAddress(addresses, address));
        }
        return List.copyOf(addresses);
    }

    private void addBrokerAddress(Set<String> addresses, String address) {
        if (address != null && !address.isBlank()) {
            addresses.add(address);
        }
    }

    void cleanup(DefaultMQPushConsumer consumer) {
        DefaultMQPushConsumerImpl implementation = consumer.getDefaultMQPushConsumerImpl();
        MQClientInstance client = implementation.getmQClientFactory();
        RuntimeException cleanupFailure = null;

        try {
            consumer.shutdown();
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to shut down RocketMQ consumer after startup failure: {}", exception.getMessage());
            LOGGER.debug("RocketMQ consumer shutdown failure details", exception);
            cleanupFailure = exception;
        }

        if (implementation.getServiceState() == ServiceState.START_FAILED) {
            try {
                if (implementation.getConsumeMessageService() != null) {
                    implementation.getConsumeMessageService().shutdown(
                            consumer.getAwaitTerminationMillisWhenShutdown()
                    );
                }
                implementation.getRebalanceImpl().destroy();
                implementation.setServiceState(ServiceState.SHUTDOWN_ALREADY);
            } catch (RuntimeException exception) {
                LOGGER.warn("Failed to fully clean RocketMQ consumer services: {}", exception.getMessage());
                LOGGER.debug("RocketMQ consumer service cleanup failure details", exception);
                cleanupFailure = combine(cleanupFailure, exception);
            }
        }

        if (client == null) {
            throwIfCleanupFailed(cleanupFailure);
            return;
        }

        String consumerGroup = consumer.getConsumerGroup();
        try {
            if (client.selectConsumer(consumerGroup) != null) {
                client.unregisterConsumer(consumerGroup);
            }
            shutdownClientFactory(client);
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to fully clean RocketMQ client factory: {}", exception.getMessage());
            LOGGER.debug("RocketMQ client factory cleanup failure details", exception);
            cleanupFailure = combine(cleanupFailure, exception);
        } finally {
            MQClientManager.getInstance().removeClientFactory(client.getClientId());
        }
        throwIfCleanupFailed(cleanupFailure);
    }

    private void shutdownClientFactory(MQClientInstance client) {
        try {
            Field serviceState = MQClientInstance.class.getDeclaredField("serviceState");
            serviceState.setAccessible(true);
            if (serviceState.get(client) == ServiceState.START_FAILED) {
                // RocketMQ 4.9.8 skips all cleanup for START_FAILED despite partially started services.
                serviceState.set(client, ServiceState.RUNNING);
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to prepare failed RocketMQ client factory for shutdown", exception);
        }
        client.shutdown();
    }

    private RuntimeException combine(RuntimeException first, RuntimeException next) {
        if (first == null) {
            return next;
        }
        first.addSuppressed(next);
        return first;
    }

    private void throwIfCleanupFailed(RuntimeException failure) {
        if (failure != null) {
            throw new IllegalStateException(
                    "RocketMQ startup cleanup failed; automatic retries are unsafe",
                    failure
            );
        }
    }
}
