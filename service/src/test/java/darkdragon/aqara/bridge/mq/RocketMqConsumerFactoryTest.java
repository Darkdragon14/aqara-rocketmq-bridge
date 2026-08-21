package darkdragon.aqara.bridge.mq;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.impl.MQClientAPIImpl;
import org.apache.rocketmq.client.impl.MQClientManager;
import org.apache.rocketmq.client.impl.consumer.ConsumeMessageConcurrentlyService;
import org.apache.rocketmq.client.impl.consumer.DefaultMQPushConsumerImpl;
import org.apache.rocketmq.client.impl.factory.MQClientInstance;
import org.apache.rocketmq.common.ServiceState;
import org.apache.rocketmq.common.protocol.ResponseCode;
import org.apache.rocketmq.common.protocol.route.BrokerData;
import org.apache.rocketmq.common.protocol.route.TopicRouteData;
import org.apache.rocketmq.remoting.exception.RemotingConnectException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RocketMqConsumerFactoryTest {

    private final RocketMqConsumerFactory consumerFactory = new RocketMqConsumerFactory();

    @Test
    void cleanupRemovesAndShutsDownCachedFactoryInStartFailedState() throws Exception {
        DefaultMQPushConsumer consumer = consumerFactory.create("test-app", null);
        DefaultMQPushConsumerImpl implementation = consumer.getDefaultMQPushConsumerImpl();
        MQClientManager manager = MQClientManager.getInstance();
        MQClientInstance poisonedFactory = manager.getOrCreateMQClientInstance(consumer);
        implementation.setmQClientFactory(poisonedFactory);
        ConsumeMessageConcurrentlyService consumeService = new ConsumeMessageConcurrentlyService(
                implementation,
                (messages, context) -> ConsumeConcurrentlyStatus.CONSUME_SUCCESS
        );
        consumeService.start();
        implementation.setConsumeMessageService(consumeService);
        implementation.setServiceState(ServiceState.START_FAILED);
        poisonedFactory.registerConsumer(consumer.getConsumerGroup(), implementation);
        setClientFactoryState(poisonedFactory, ServiceState.START_FAILED);

        consumerFactory.cleanup(consumer);

        MQClientInstance replacementFactory = manager.getOrCreateMQClientInstance(consumer);
        try {
            assertThat(replacementFactory).isNotSameAs(poisonedFactory);
            assertThat(poisonedFactory.selectConsumer(consumer.getConsumerGroup())).isNull();
            assertThat(poisonedFactory.getScheduledExecutorService().isShutdown()).isTrue();
            assertExecutorIsShutdown(consumeService, "scheduledExecutorService");
            assertExecutorIsShutdown(consumeService, "consumeExecutor");
            assertExecutorIsShutdown(consumeService, "cleanExpireMsgExecutors");
        } finally {
            manager.removeClientFactory(replacementFactory.getClientId());
        }
    }

    @Test
    void eachConsumerUsesAnIsolatedClientId() {
        DefaultMQPushConsumer first = consumerFactory.create("test-app", null);
        DefaultMQPushConsumer second = consumerFactory.create("test-app", null);

        assertThat(first.buildMQClientId()).isNotEqualTo(second.buildMQClientId());
    }

    @Test
    void cleanupFailureIsPropagated() {
        DefaultMQPushConsumer consumer = mock(DefaultMQPushConsumer.class);
        DefaultMQPushConsumerImpl implementation = mock(DefaultMQPushConsumerImpl.class);
        when(consumer.getDefaultMQPushConsumerImpl()).thenReturn(implementation);
        when(implementation.getServiceState()).thenReturn(ServiceState.SHUTDOWN_ALREADY);
        doThrow(new IllegalStateException("shutdown failed")).when(consumer).shutdown();

        assertThatThrownBy(() -> consumerFactory.cleanup(consumer))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("automatic retries are unsafe")
                .hasRootCauseMessage("shutdown failed");
    }

    @Test
    void validationUsesNextBrokerWhenFirstIsUnavailable() throws Exception {
        TopicRouteData route = new TopicRouteData();
        route.setBrokerDatas(List.of(
                broker("first", "127.0.0.1:1"),
                broker("second", "127.0.0.1:2")
        ));
        ValidationFixture fixture = validationFixture(route);
        doThrow(new RemotingConnectException("127.0.0.1:1"))
                .when(fixture.api()).sendHeartbeat(eq("127.0.0.1:1"), any(), eq(0L));

        consumerFactory.validateConnection(fixture.consumer(), "test-app");

        verify(fixture.api()).sendHeartbeat(eq("127.0.0.1:1"), any(), eq(0L));
        verify(fixture.api()).sendHeartbeat(eq("127.0.0.1:2"), any(), eq(0L));
    }

    @Test
    void validationUsesSlaveWhenMasterIsUnavailable() throws Exception {
        TopicRouteData route = new TopicRouteData();
        route.setBrokerDatas(List.of(broker("group", Map.of(
                0L, "127.0.0.1:1",
                1L, "127.0.0.1:2"
        ))));
        ValidationFixture fixture = validationFixture(route);
        doThrow(new RemotingConnectException("127.0.0.1:1"))
                .when(fixture.api()).sendHeartbeat(eq("127.0.0.1:1"), any(), eq(0L));

        consumerFactory.validateConnection(fixture.consumer(), "test-app");

        InOrder order = inOrder(fixture.api());
        order.verify(fixture.api()).sendHeartbeat(eq("127.0.0.1:1"), any(), eq(0L));
        order.verify(fixture.api()).sendHeartbeat(eq("127.0.0.1:2"), any(), eq(0L));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void permanentBrokerFailureTakesPriorityOverNetworkFailure(boolean networkFailureFirst) throws Exception {
        BrokerData networkBroker = broker("network", "127.0.0.1:1");
        BrokerData deniedBroker = broker("denied", "127.0.0.1:2");
        TopicRouteData route = new TopicRouteData();
        route.setBrokerDatas(networkFailureFirst
                ? List.of(networkBroker, deniedBroker)
                : List.of(deniedBroker, networkBroker));
        ValidationFixture fixture = validationFixture(route);
        MQBrokerException permissionFailure = new MQBrokerException(ResponseCode.NO_PERMISSION, "access denied");
        doThrow(new RemotingConnectException("127.0.0.1:1"))
                .when(fixture.api()).sendHeartbeat(eq("127.0.0.1:1"), any(), eq(0L));
        doThrow(permissionFailure)
                .when(fixture.api()).sendHeartbeat(eq("127.0.0.1:2"), any(), eq(0L));

        assertThatThrownBy(() -> consumerFactory.validateConnection(fixture.consumer(), "test-app"))
                .isSameAs(permissionFailure);

        verify(fixture.api()).sendHeartbeat(eq("127.0.0.1:1"), any(), eq(0L));
        verify(fixture.api()).sendHeartbeat(eq("127.0.0.1:2"), any(), eq(0L));
        assertThat(permissionFailure.getSuppressed())
                .singleElement()
                .isInstanceOf(RemotingConnectException.class);
    }

    @Test
    void nameserverLookupInterruptionRestoresInterruptFlag() throws Exception {
        ValidationFixture fixture = validationFixture(new TopicRouteData());
        doThrow(new InterruptedException("lookup interrupted"))
                .when(fixture.api()).getTopicRouteInfoFromNameServer("test-app", 0);

        try {
            assertThatThrownBy(() -> consumerFactory.validateConnection(fixture.consumer(), "test-app"))
                    .isInstanceOf(InterruptedException.class)
                    .hasMessage("lookup interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void heartbeatInterruptionRestoresInterruptFlag() throws Exception {
        TopicRouteData route = new TopicRouteData();
        route.setBrokerDatas(List.of(
                broker("first", "127.0.0.1:1"),
                broker("second", "127.0.0.1:2")
        ));
        ValidationFixture fixture = validationFixture(route);
        doThrow(new InterruptedException("heartbeat interrupted"))
                .when(fixture.api()).sendHeartbeat(eq("127.0.0.1:1"), any(), eq(0L));

        try {
            assertThatThrownBy(() -> consumerFactory.validateConnection(fixture.consumer(), "test-app"))
                    .isInstanceOf(InterruptedException.class)
                    .hasMessage("heartbeat interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(fixture.api(), never()).sendHeartbeat(eq("127.0.0.1:2"), any(), eq(0L));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void malformedBrokerEntriesProduceControlledNoBrokerFailure() throws Exception {
        HashMap<Long, String> malformedAddresses = new HashMap<>();
        malformedAddresses.put(null, "127.0.0.1:1");
        malformedAddresses.put(1L, null);
        malformedAddresses.put(2L, " ");
        List<BrokerData> brokerDatas = new ArrayList<>();
        brokerDatas.add(null);
        brokerDatas.add(new BrokerData("test-cluster", "missing-map", null));
        brokerDatas.add(new BrokerData("test-cluster", "malformed-map", malformedAddresses));
        TopicRouteData route = new TopicRouteData();
        route.setBrokerDatas(brokerDatas);
        ValidationFixture fixture = validationFixture(route);

        assertThatThrownBy(() -> consumerFactory.validateConnection(fixture.consumer(), "test-app"))
                .isInstanceOf(MQClientException.class)
                .hasMessageContaining("No broker available for topic test-app");

        verify(fixture.api(), never()).sendHeartbeat(any(), any(), anyLong());
    }

    private BrokerData broker(String name, String address) {
        return broker(name, Map.of(0L, address));
    }

    private BrokerData broker(String name, Map<Long, String> brokerAddresses) {
        HashMap<Long, String> addresses = new HashMap<>(brokerAddresses);
        return new BrokerData("test-cluster", name, addresses);
    }

    private ValidationFixture validationFixture(TopicRouteData route) throws Exception {
        DefaultMQPushConsumer consumer = mock(DefaultMQPushConsumer.class);
        DefaultMQPushConsumerImpl implementation = mock(DefaultMQPushConsumerImpl.class);
        MQClientInstance client = mock(MQClientInstance.class);
        MQClientAPIImpl api = mock(MQClientAPIImpl.class);
        when(consumer.getDefaultMQPushConsumerImpl()).thenReturn(implementation);
        when(consumer.getConsumerGroup()).thenReturn("test-app");
        when(implementation.getmQClientFactory()).thenReturn(client);
        when(implementation.getSubscriptionInner()).thenReturn(new ConcurrentHashMap<>());
        when(client.getMQClientAPIImpl()).thenReturn(api);
        when(api.getTopicRouteInfoFromNameServer("test-app", 0)).thenReturn(route);
        return new ValidationFixture(consumer, api);
    }

    private record ValidationFixture(DefaultMQPushConsumer consumer, MQClientAPIImpl api) {
    }

    private void setClientFactoryState(MQClientInstance client, ServiceState state) throws Exception {
        Field serviceState = MQClientInstance.class.getDeclaredField("serviceState");
        serviceState.setAccessible(true);
        serviceState.set(client, state);
    }

    private void assertExecutorIsShutdown(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        assertThat(((ExecutorService) field.get(target)).isShutdown()).isTrue();
    }
}
