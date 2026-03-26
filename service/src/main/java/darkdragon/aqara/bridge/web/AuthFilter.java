package darkdragon.aqara.bridge.web;

import darkdragon.aqara.bridge.config.BridgeProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class AuthFilter implements WebFilter {

    private final BridgeProperties bridgeProperties;

    public AuthFilter(BridgeProperties bridgeProperties) {
        this.bridgeProperties = bridgeProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!"/events".equals(exchange.getRequest().getPath().value())) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String expected = "Bearer " + bridgeProperties.getBridgeToken();
        if (expected.equals(authorization)) {
            return chain.filter(exchange);
        }

        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
