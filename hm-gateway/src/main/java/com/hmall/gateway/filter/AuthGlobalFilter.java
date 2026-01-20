package com.hmall.gateway.filter;

import com.hmall.gateway.config.AuthProperties;
import com.hmall.gateway.utils.JwtTool;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 这是网关的全局过滤器
 * 作用：1.拦截非法请求  2.保存用户信息到请求头中去token->request头
 * 网关到微服务是一次新的http请求
 */
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AuthProperties.class) //启用并注册指定的 @ConfigurationProperties 类为 Spring 容器中的 Bean
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private final AuthProperties authProperties;

    private final AntPathMatcher antPathMatcher = new AntPathMatcher();  //路径匹配的

    private final JwtTool jwtTool;


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        //获取请求
        ServerHttpRequest request = exchange.getRequest();
        //获取请求路径，看是否需要放过
        if (isExclude(request.getPath().toString())) {
            return chain.filter(exchange);
        }
        //获取请求头中的token
        String token = null;
        List<String> headers = request.getHeaders().get("authorization");
        if (headers != null && !headers.isEmpty()) {
            token = headers.get(0);
        }
        //解析token拿到其中存放的数据（用户id）
        Long userId = null;
        try {
            userId = jwtTool.parseToken(token);
        } catch (Exception e) {
            // 如果无效，拦截
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);   //401
            return response.setComplete();  //完结，不会再执行后续的filter方法
        }
        //保存用户信息到请求头（传递用户信息）
        //这就要修改原始的请求头了，新增数据用exchange.mutate方法
        String userInfo = userId.toString();
        ServerWebExchange newExchange = exchange.mutate()
                .request(builder -> builder.header("user-info", userInfo))
                .build();

        return chain.filter(newExchange);
    }

    //获取请求路径，看是否需要放过
    private boolean isExclude(String antPath) {
        for (String excludePath : authProperties.getExcludePaths()) {
            if (antPathMatcher.match(excludePath, antPath)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
