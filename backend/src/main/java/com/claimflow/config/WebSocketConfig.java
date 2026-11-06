package com.claimflow.config;

import com.claimflow.events.ClaimEventsSocketHandler;
import com.claimflow.events.JwtHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ClaimEventsSocketHandler handler;
    private final JwtHandshakeInterceptor interceptor;
    private final AppProperties props;

    public WebSocketConfig(ClaimEventsSocketHandler handler, JwtHandshakeInterceptor interceptor, AppProperties props) {
        this.handler = handler;
        this.interceptor = interceptor;
        this.props = props;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws")
                .addInterceptors(interceptor)
                .setAllowedOriginPatterns(props.cors().allowedOrigins().toArray(String[]::new));
    }
}
