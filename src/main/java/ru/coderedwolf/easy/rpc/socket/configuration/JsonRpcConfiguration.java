package ru.coderedwolf.easy.rpc.socket.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import ru.coderedwolf.easy.rpc.socket.core.JsonRpcSendingTemplate;
import ru.coderedwolf.easy.rpc.socket.jsonRpc.TransportWebSocketHandler;
import ru.coderedwolf.easy.rpc.socket.support.AbstractSubscribeChannel;
import ru.coderedwolf.easy.rpc.socket.support.ExecutorSubscribableChannel;
import ru.coderedwolf.easy.rpc.socket.jsonRpc.JsonRpcAnnotationMessageHandler;

/**
 * All necessary bins for the library.
 */
@Configuration
public class JsonRpcConfiguration {

    @Bean
    public AbstractSubscribeChannel clientInboundChannel() {
        return new ExecutorSubscribableChannel(clientInboundChannelExecutor());
    }

    @Bean
    public AbstractSubscribeChannel clientOutboundChannel() {
        return new ExecutorSubscribableChannel(clientOutboundChannelExecutor());
    }

    @Bean
    public ThreadPoolTaskExecutor clientInboundChannelExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("clientInboundChannel-");
        return executor;
    }

    @Bean
    public ThreadPoolTaskExecutor clientOutboundChannelExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("clientOutboundChannel-");
        return executor;
    }

    @Bean
    public JsonRpcAnnotationMessageHandler jsonRpcAnnotationMessageHandler() {
        return new JsonRpcAnnotationMessageHandler(clientInboundChannel(),
                clientOutboundChannel());
    }

    @Bean
    public JsonRpcSendingTemplate jsonRpcSendingTemplate() {
        return new JsonRpcSendingTemplate(clientOutboundChannel());
    }

    @Bean
    public TransportWebSocketHandler transportWebSocketHandler() {
        return new TransportWebSocketHandler(clientOutboundChannel(), clientInboundChannel());
    }
}
