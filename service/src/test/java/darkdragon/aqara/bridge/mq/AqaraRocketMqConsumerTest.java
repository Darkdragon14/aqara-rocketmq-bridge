package darkdragon.aqara.bridge.mq;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import darkdragon.aqara.bridge.config.BridgeProperties;
import darkdragon.aqara.bridge.stream.EventBroadcaster;
import io.netty.channel.ChannelHandlerContext;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.constant.PermName;
import org.apache.rocketmq.common.protocol.RequestCode;
import org.apache.rocketmq.common.protocol.ResponseCode;
import org.apache.rocketmq.common.protocol.route.BrokerData;
import org.apache.rocketmq.common.protocol.route.QueueData;
import org.apache.rocketmq.common.protocol.route.TopicRouteData;
import org.apache.rocketmq.remoting.exception.RemotingCommandException;
import org.apache.rocketmq.remoting.exception.RemotingSendRequestException;
import org.apache.rocketmq.remoting.netty.NettyRemotingServer;
import org.apache.rocketmq.remoting.netty.NettyRequestProcessor;
import org.apache.rocketmq.remoting.netty.NettyServerConfig;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.scheduling.TaskScheduler;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class AqaraRocketMqConsumerTest {

    @Mock
    private RocketMqMessageParser messageParser;

    @Mock
    private EventBroadcaster eventBroadcaster;

    @Mock
    private RocketMqConsumerFactory consumerFactory;

    @Mock
    private TaskScheduler taskScheduler;

    private BridgeProperties properties;
    private RocketMqHealth health;
    private AqaraRocketMqConsumer rocketMqConsumer;

    @BeforeEach
    void setUp() {
        properties = properties();
        health = new RocketMqHealth();
        rocketMqConsumer = new AqaraRocketMqConsumer(
                properties,
                messageParser,
                eventBroadcaster,
                health,
                consumerFactory,
                taskScheduler
        );
    }

    @Test
    void transientValidationFailureSchedulesRetryAndRecoversWithFreshConsumer() throws Exception {
        DefaultMQPushConsumer failedConsumer = mock(DefaultMQPushConsumer.class);
        DefaultMQPushConsumer recoveredConsumer = mock(DefaultMQPushConsumer.class);
        ScheduledFuture<?> retryFuture = mock(ScheduledFuture.class);
        MQClientException failure = transientFailure();
        Logger logger = (Logger) LoggerFactory.getLogger(AqaraRocketMqConsumer.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        when(consumerFactory.create(anyString(), any())).thenReturn(failedConsumer, recoveredConsumer);
        doThrow(failure).when(consumerFactory).validateConnection(failedConsumer, "test-app");
        doReturn(retryFuture).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));

        try {
            assertThatCode(() -> rocketMqConsumer.run(mock(ApplicationArguments.class)))
                    .doesNotThrowAnyException();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        verify(consumerFactory).cleanup(failedConsumer);
        verify(failedConsumer).start();
        assertThat(health.isStarted()).isFalse();
        assertThat(health.getLastError()).isEqualTo("send request to <nameserver:9876> failed");
        assertThat(appender.list).filteredOn(event -> event.getLevel() == ch.qos.logback.classic.Level.WARN)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getFormattedMessage()).isEqualTo(
                "RocketMQ connection to nameserver:9876 failed: send request to <nameserver:9876> failed. "
                        + "Retrying in 5 seconds"
                    );
                    assertThat(event.getThrowableProxy()).isNull();
                });

        ArgumentCaptor<Runnable> retry = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(retry.capture(), any(Instant.class));
        retry.getValue().run();

        verify(recoveredConsumer).start();
        verify(consumerFactory, times(2)).create(anyString(), any());
        assertThat(health.isStarted()).isTrue();
        assertThat(health.getLastError()).isNull();
    }

    @Test
    void cleanupFailureStopsAutomaticRetries() throws Exception {
        DefaultMQPushConsumer failedConsumer = mock(DefaultMQPushConsumer.class);
        when(consumerFactory.create(anyString(), any())).thenReturn(failedConsumer);
        doThrow(transientFailure()).when(consumerFactory).validateConnection(failedConsumer, "test-app");
        doThrow(new IllegalStateException("cleanup failed")).when(consumerFactory).cleanup(failedConsumer);

        assertThatThrownBy(() -> rocketMqConsumer.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("cleanup failed");

        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
        assertThat(health.isFailed()).isTrue();
        assertThat(health.getLastError()).isEqualTo("cleanup failed");
    }

    @Test
    void pendingRetryPreventsAnotherConsumerFromStarting() throws Exception {
        DefaultMQPushConsumer failedConsumer = mock(DefaultMQPushConsumer.class);
        doThrow(transientFailure()).when(failedConsumer).start();
        when(consumerFactory.create(anyString(), any())).thenReturn(failedConsumer);
        doReturn(mock(ScheduledFuture.class)).when(taskScheduler)
                .schedule(any(Runnable.class), any(Instant.class));

        rocketMqConsumer.run(mock(ApplicationArguments.class));
        rocketMqConsumer.run(mock(ApplicationArguments.class));

        verify(consumerFactory).create(anyString(), any());
        verify(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void shutdownCancelsPendingRetryAndPreventsLaterAttempt() throws Exception {
        DefaultMQPushConsumer failedConsumer = mock(DefaultMQPushConsumer.class);
        ScheduledFuture<?> retryFuture = mock(ScheduledFuture.class);
        doThrow(transientFailure()).when(failedConsumer).start();
        when(consumerFactory.create(anyString(), any())).thenReturn(failedConsumer);
        doReturn(retryFuture).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));

        rocketMqConsumer.run(mock(ApplicationArguments.class));
        ArgumentCaptor<Runnable> retry = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(retry.capture(), any(Instant.class));

        rocketMqConsumer.stopConsumer();
        retry.getValue().run();

        verify(retryFuture).cancel(false);
        verify(consumerFactory).create(anyString(), any());
        assertThat(health.isStarted()).isFalse();
    }

    @Test
    void shutdownFailureStillMarksStoppedAndPropagatesFailure() throws Exception {
        DefaultMQPushConsumer activeConsumer = mock(DefaultMQPushConsumer.class);
        when(consumerFactory.create(anyString(), any())).thenReturn(activeConsumer);
        doThrow(new IllegalStateException("shutdown failed")).when(activeConsumer).shutdown();
        rocketMqConsumer.run(mock(ApplicationArguments.class));
        assertThat(health.isStarted()).isTrue();

        assertThatThrownBy(rocketMqConsumer::stopConsumer)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("shutdown failed");

        assertThat(health.isStarted()).isFalse();
    }

    @Test
    void shutdownWaitsForBlockedValidationAndPreventsConsumerPublication() throws Exception {
        DefaultMQPushConsumer connectingConsumer = mock(DefaultMQPushConsumer.class);
        CountDownLatch validationStarted = new CountDownLatch(1);
        CountDownLatch releaseValidation = new CountDownLatch(1);
        when(consumerFactory.create(anyString(), any())).thenReturn(connectingConsumer);
        org.mockito.Mockito.doAnswer(invocation -> {
            validationStarted.countDown();
            releaseValidation.await();
            return null;
        }).when(consumerFactory).validateConnection(connectingConsumer, "test-app");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> startup = executor.submit(() -> {
                try {
                    rocketMqConsumer.run(mock(ApplicationArguments.class));
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });
            assertThat(validationStarted.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> shutdown = executor.submit(rocketMqConsumer::stopConsumer);
            assertThatThrownBy(() -> shutdown.get(100, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseValidation.countDown();
            startup.get(5, TimeUnit.SECONDS);
            shutdown.get(5, TimeUnit.SECONDS);
        } finally {
            releaseValidation.countDown();
            executor.shutdownNow();
        }

        verify(consumerFactory).cleanup(connectingConsumer);
        verify(connectingConsumer, never()).shutdown();
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
        assertThat(health.isStarted()).isFalse();
    }

    @Test
    void nonRetryableInitialFailureStillFailsApplicationStartup() throws Exception {
        DefaultMQPushConsumer failedConsumer = mock(DefaultMQPushConsumer.class);
        MQClientException failure = new MQClientException("bad credentials", null);
        doThrow(failure).when(failedConsumer).start();
        when(consumerFactory.create(anyString(), any())).thenReturn(failedConsumer);

        assertThatThrownBy(() -> rocketMqConsumer.run(mock(ApplicationArguments.class)))
                .isSameAs(failure);

        verify(consumerFactory).cleanup(failedConsumer);
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
        assertThat(health.isFailed()).isTrue();
        assertThat(health.getLastError()).startsWith("bad credentials");
    }

    @Test
    void nonRetryableFailureDuringRetryStopsRetrying() throws Exception {
        DefaultMQPushConsumer transientFailureConsumer = mock(DefaultMQPushConsumer.class);
        DefaultMQPushConsumer permanentFailureConsumer = mock(DefaultMQPushConsumer.class);
        doThrow(transientFailure()).when(transientFailureConsumer).start();
        doThrow(new MQClientException("bad credentials", null)).when(permanentFailureConsumer).start();
        when(consumerFactory.create(anyString(), any()))
                .thenReturn(transientFailureConsumer, permanentFailureConsumer);
        doReturn(mock(ScheduledFuture.class)).when(taskScheduler)
                .schedule(any(Runnable.class), any(Instant.class));

        rocketMqConsumer.run(mock(ApplicationArguments.class));
        ArgumentCaptor<Runnable> retry = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(retry.capture(), any(Instant.class));
        retry.getValue().run();

        verify(consumerFactory).cleanup(permanentFailureConsumer);
        verify(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
        assertThat(health.isStarted()).isFalse();
        assertThat(health.isFailed()).isTrue();
        assertThat(health.getLastError()).startsWith("bad credentials");
    }

    @ParameterizedTest
    @ValueSource(ints = {ResponseCode.SYSTEM_BUSY, ResponseCode.SYSTEM_ERROR})
    void transientBrokerResponseSchedulesRetry(int responseCode) throws Exception {
        DefaultMQPushConsumer failedConsumer = mock(DefaultMQPushConsumer.class);
        doThrow(new MQBrokerException(responseCode, "broker temporarily unavailable"))
                .when(consumerFactory).validateConnection(failedConsumer, "test-app");
        when(consumerFactory.create(anyString(), any())).thenReturn(failedConsumer);
        doReturn(mock(ScheduledFuture.class)).when(taskScheduler)
                .schedule(any(Runnable.class), any(Instant.class));

        assertThatCode(() -> rocketMqConsumer.run(mock(ApplicationArguments.class)))
                .doesNotThrowAnyException();

        verify(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
        assertThat(health.isFailed()).isFalse();
        assertThat(health.isStarted()).isFalse();
        assertThat(health.getLastError()).isEqualTo("broker temporarily unavailable");
    }

    @Test
    void remotingCommandFailureIsNotRetried() throws Exception {
        DefaultMQPushConsumer failedConsumer = mock(DefaultMQPushConsumer.class);
        MQClientException failure = new MQClientException(
                "invalid protocol response",
                new RemotingCommandException("malformed command")
        );
        doThrow(failure).when(failedConsumer).start();
        when(consumerFactory.create(anyString(), any())).thenReturn(failedConsumer);

        assertThatThrownBy(() -> rocketMqConsumer.run(mock(ApplicationArguments.class)))
                .isSameAs(failure);

        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
        assertThat(health.isFailed()).isTrue();
    }

    @Test
    void nameserverPermissionDenialIsDetectedAsPermanentStartupFailure() {
        NettyServerConfig serverConfig = new NettyServerConfig();
        serverConfig.setListenPort(0);
        NettyRemotingServer nameserver = new NettyRemotingServer(serverConfig);
        nameserver.registerProcessor(RequestCode.GET_ROUTEINFO_BY_TOPIC, new NettyRequestProcessor() {
            @Override
            public RemotingCommand processRequest(ChannelHandlerContext context, RemotingCommand request) {
                return RemotingCommand.createResponseCommand(ResponseCode.NO_PERMISSION, "access denied");
            }

            @Override
            public boolean rejectRequest() {
                return false;
            }
        }, null);
        nameserver.start();

        BridgeProperties deniedProperties = properties();
        deniedProperties.setMqNamesrvAddr("127.0.0.1:" + nameserver.localListenPort());
        RocketMqHealth deniedHealth = new RocketMqHealth();
        AqaraRocketMqConsumer deniedConsumer = new AqaraRocketMqConsumer(
                deniedProperties,
                messageParser,
                eventBroadcaster,
                deniedHealth,
                new RocketMqConsumerFactory(),
                taskScheduler
        );

        try {
            assertThatThrownBy(() -> deniedConsumer.run(mock(ApplicationArguments.class)))
                    .isInstanceOf(MQClientException.class)
                    .hasMessageContaining("access denied");
        } finally {
            deniedConsumer.stopConsumer();
            nameserver.shutdown();
        }

        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
        assertThat(deniedHealth.isStarted()).isFalse();
        assertThat(deniedHealth.isFailed()).isTrue();
        assertThat(deniedHealth.getLastError()).contains("access denied");
    }

    @Test
    void brokerPermissionDenialIsDetectedBySignedHeartbeat() {
        AtomicReference<RemotingCommand> receivedHeartbeat = new AtomicReference<>();
        NettyRemotingServer broker = remotingServer();
        broker.registerProcessor(RequestCode.HEART_BEAT, new NettyRequestProcessor() {
            @Override
            public RemotingCommand processRequest(ChannelHandlerContext context, RemotingCommand request) {
                receivedHeartbeat.set(request);
                return RemotingCommand.createResponseCommand(ResponseCode.NO_PERMISSION, "broker access denied");
            }

            @Override
            public boolean rejectRequest() {
                return false;
            }
        }, null);
        broker.start();

        TopicRouteData route = topicRoute("127.0.0.1:" + broker.localListenPort());
        NettyRemotingServer nameserver = remotingServer();
        nameserver.registerProcessor(RequestCode.GET_ROUTEINFO_BY_TOPIC, new NettyRequestProcessor() {
            @Override
            public RemotingCommand processRequest(ChannelHandlerContext context, RemotingCommand request) {
                RemotingCommand response = RemotingCommand.createResponseCommand(ResponseCode.SUCCESS, null);
                response.setBody(route.encode());
                return response;
            }

            @Override
            public boolean rejectRequest() {
                return false;
            }
        }, null);
        nameserver.start();

        BridgeProperties deniedProperties = properties();
        deniedProperties.setMqNamesrvAddr("127.0.0.1:" + nameserver.localListenPort());
        RocketMqHealth deniedHealth = new RocketMqHealth();
        AqaraRocketMqConsumer deniedConsumer = new AqaraRocketMqConsumer(
                deniedProperties,
                messageParser,
                eventBroadcaster,
                deniedHealth,
                new RocketMqConsumerFactory(),
                taskScheduler
        );

        try {
            assertThatThrownBy(() -> deniedConsumer.run(mock(ApplicationArguments.class)))
                    .hasMessageContaining("broker access denied");
        } finally {
            deniedConsumer.stopConsumer();
            nameserver.shutdown();
            broker.shutdown();
        }

        RemotingCommand heartbeat = receivedHeartbeat.get();
        assertThat(heartbeat).isNotNull();
        assertThat(heartbeat.getExtFields().get(SessionCredentials.ACCESS_KEY)).isEqualTo("test-key");
        assertThat(heartbeat.getExtFields().get(SessionCredentials.SIGNATURE)).isNotBlank();
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
        assertThat(deniedHealth.isFailed()).isTrue();
    }

    @Test
    void realConsumerRecoversAfterBrokerBecomesReachable(CapturedOutput output) throws Exception {
        NettyRemotingServer broker = remotingServer();
        broker.registerProcessor(RequestCode.HEART_BEAT, successProcessor(), null);
        broker.registerProcessor(RequestCode.UNREGISTER_CLIENT, successProcessor(), null);
        broker.start();

        AtomicReference<TopicRouteData> currentRoute = new AtomicReference<>(
                topicRoute("127.0.0.1:1")
        );
        NettyRemotingServer nameserver = remotingServer();
        nameserver.registerProcessor(RequestCode.GET_ROUTEINFO_BY_TOPIC, new NettyRequestProcessor() {
            @Override
            public RemotingCommand processRequest(ChannelHandlerContext context, RemotingCommand request) {
                RemotingCommand response = RemotingCommand.createResponseCommand(ResponseCode.SUCCESS, null);
                response.setBody(currentRoute.get().encode());
                return response;
            }

            @Override
            public boolean rejectRequest() {
                return false;
            }
        }, null);
        nameserver.start();

        BridgeProperties reconnectProperties = properties();
        reconnectProperties.setMqNamesrvAddr("127.0.0.1:" + nameserver.localListenPort());
        RocketMqHealth reconnectHealth = new RocketMqHealth();
        AqaraRocketMqConsumer reconnectingConsumer = new AqaraRocketMqConsumer(
                reconnectProperties,
                messageParser,
                eventBroadcaster,
                reconnectHealth,
                new RocketMqConsumerFactory(),
                taskScheduler
        );
        doReturn(mock(ScheduledFuture.class)).when(taskScheduler)
                .schedule(any(Runnable.class), any(Instant.class));

        try {
            reconnectingConsumer.run(mock(ApplicationArguments.class));
            assertThat(reconnectHealth.isStarted()).isFalse();
            assertThat(output).contains("Retrying in 5 seconds");
            assertThat(output).doesNotContain("\tat ");

            ArgumentCaptor<Runnable> retry = ArgumentCaptor.forClass(Runnable.class);
            verify(taskScheduler).schedule(retry.capture(), any(Instant.class));
            currentRoute.set(topicRoute("127.0.0.1:" + broker.localListenPort()));
            retry.getValue().run();

            assertThat(reconnectHealth.isStarted()).isTrue();
            assertThat(reconnectHealth.getLastError()).isNull();
        } finally {
            reconnectingConsumer.stopConsumer();
            nameserver.shutdown();
            broker.shutdown();
        }
    }

    @Test
    void retryDelayUsesExponentialBackoffWithFiveMinuteCap() {
        assertThat(AqaraRocketMqConsumer.retryDelay(1)).isEqualTo(Duration.ofSeconds(5));
        assertThat(AqaraRocketMqConsumer.retryDelay(2)).isEqualTo(Duration.ofSeconds(10));
        assertThat(AqaraRocketMqConsumer.retryDelay(6)).isEqualTo(Duration.ofSeconds(160));
        assertThat(AqaraRocketMqConsumer.retryDelay(7)).isEqualTo(Duration.ofMinutes(5));
        assertThat(AqaraRocketMqConsumer.retryDelay(100)).isEqualTo(Duration.ofMinutes(5));
    }

    private MQClientException transientFailure() {
        return new MQClientException(
                "consumer startup failed",
                new RemotingSendRequestException("nameserver:9876")
        );
    }

    private BridgeProperties properties() {
        BridgeProperties result = new BridgeProperties();
        result.setAppId("test-app");
        result.setKeyId("test-key");
        result.setAppKey("test-secret");
        result.setMqNamesrvAddr("nameserver:9876");
        return result;
    }

    private NettyRemotingServer remotingServer() {
        NettyServerConfig config = new NettyServerConfig();
        config.setListenPort(0);
        return new NettyRemotingServer(config);
    }

    private TopicRouteData topicRoute(String brokerAddress) {
        HashMap<Long, String> brokerAddresses = new HashMap<>();
        brokerAddresses.put(0L, brokerAddress);
        BrokerData brokerData = new BrokerData("test-cluster", "test-broker", brokerAddresses);

        QueueData queueData = new QueueData();
        queueData.setBrokerName("test-broker");
        queueData.setReadQueueNums(1);
        queueData.setWriteQueueNums(1);
        queueData.setPerm(PermName.PERM_READ | PermName.PERM_WRITE);

        TopicRouteData route = new TopicRouteData();
        route.setBrokerDatas(List.of(brokerData));
        route.setQueueDatas(List.of(queueData));
        return route;
    }

    private NettyRequestProcessor successProcessor() {
        return new NettyRequestProcessor() {
            @Override
            public RemotingCommand processRequest(ChannelHandlerContext context, RemotingCommand request) {
                return RemotingCommand.createResponseCommand(ResponseCode.SUCCESS, null);
            }

            @Override
            public boolean rejectRequest() {
                return false;
            }
        };
    }
}
