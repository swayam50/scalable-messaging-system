package io.wulfcodes.messaging.chat.config;

import io.wulfcodes.messaging.chat.websocket.ChatWebSocketHandler;
import io.wulfcodes.messaging.chat.websocket.JwtHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * Raw WebSocket (no STOMP broker): we own routing ourselves, which is what the
 * consistent-hash multi-node design in milestone 4 needs.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    public static final String CHAT_PATH = "/ws/chat";

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final ChatProperties properties;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, CHAT_PATH)
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOrigins(properties.cors().allowedOrigins().toArray(String[]::new));
    }

    /** Frame size limits and idle timeout for the servlet container's WebSocket support. */
    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(64 * 1024);
        container.setMaxSessionIdleTimeout(10 * 60 * 1000L);
        return container;
    }
}
