package cn.edu.sztu.elevatormonitor.config;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

/**
 * MQTT 配置 — 接收嵌入式设备通过 MQTT 协议上报的电梯数据。
 *
 * <h3>架构</h3>
 * <pre>
 *   嵌入式设备 → MQTT Broker → MqttPahoMessageDrivenChannelAdapter
 *       → mqttInputChannel → MqttMessageReceiver (路由到 MNKApplicationService)
 * </pre>
 *
 * <h3>Topic 约定</h3>
 * <pre>
 *   elevator/mnk/data        — MNK 协议数据 (payload = 94字节HEX字符串)
 *   elevator/{deviceId}/data — 按设备ID区分的数据 (可选)
 * </pre>
 *
 * @author mqtt-integration
 * @since 0.3.0
 */
@Configuration
public class MqttConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttConfig.class);

    @Value("${mqtt.broker.url:tcp://127.0.0.1:1883}")
    private String brokerUrl;

    @Value("${mqtt.client.id:elevator-monitor-backend}")
    private String clientId;

    @Value("${mqtt.topic:elevator/mnk/#}")
    private String topic;

    @Value("${mqtt.username:}")
    private String username;

    @Value("${mqtt.password:}")
    private String password;

    @Value("${mqtt.qos:1}")
    private int qos;

    /**
     * MQTT 客户端工厂。
     */
    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{brokerUrl});
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(60);
        if (username != null && !username.isEmpty()) {
            options.setUserName(username);
        }
        if (password != null && !password.isEmpty()) {
            options.setPassword(password.toCharArray());
        }
        factory.setConnectionOptions(options);
        LOGGER.info("[MQTT] 客户端工厂已配置, broker={}, clientId={}", brokerUrl, clientId);
        return factory;
    }

    /**
     * MQTT 入站通道。
     */
    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    /**
     * MQTT 消息驱动适配器 — 订阅 Topic 并将消息路由到 mqttInputChannel。
     * 实际消息处理由 {@link cn.edu.sztu.elevatormonitor.services.MqttMessageReceiver} 中的
     * {@code @ServiceActivator(inputChannel = "mqttInputChannel")} 完成。
     */
    @Bean
    public MessageProducer mqttInbound() {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(clientId, mqttClientFactory(), topic.split(","));
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(qos);
        adapter.setOutputChannel(mqttInputChannel());
        LOGGER.info("[MQTT] 适配器已创建, topic={}, qos={}", topic, qos);
        return adapter;
    }
}
