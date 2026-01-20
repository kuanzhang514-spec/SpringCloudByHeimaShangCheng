package com.hmall.api.config;

import com.hmall.common.utils.UserContext;
import feign.Logger;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;

/**
 * 配置openFeign的日志级别 and 发起openFeign调用时将用户信息放在请求头中***
 */
public class DefaultFeignConfig {
    @Bean
    public Logger.Level feignLogLevel() {
        return Logger.Level.FULL;
    }

    /**
     * 采用匿名内部类的方式注册Bean
     * 借助Feign中提供的一个拦截器接口：feign.RequestInterceptor
     * 目的: ---> 让每一个由OpenFeign发起的请求自动携带登录用户信息***
     * <p>
     * 由于微服务获取用户信息是通过拦截器在请求头中读取，（在hm-common中已经实现了）
     * 因此要想实现微服务之间的用户信息传递，就必须在微服务发起调用时把用户信息存入请求头。（和网关中存入用户信息到请求头中的方法类似）
     * <p>
     * 每次通过OpenFeign发起的调用都会导致一个新的HTTP请求(自己发的，浏览器端监测不到)被发送到目标服务端点。
     * 这与传统的在同一应用上下文中直接调用本地方法有本质上的不同，是一种跨网络的服务间通信方式。
     * <p>
     * openFeign发起的请求，不会经过网关，但是会经过common模块里面配置的interceptor拦截器***
     * 当在一个微服务中使用OpenFeign调用另一个微服务时，无论是从代码执行的角度还是网络通信的角度来看，这都不会保持在同一个线程内
     *
     * @return
     */
    @Bean
    public RequestInterceptor userInfoRequestInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                // 获取登录用户（从当前线程中取，因为openFeign远程调用依旧是同一个线程发起的）
                Long userId = UserContext.getUser();
                if (userId == null) {
                    // 如果为空则直接跳过
                    return;
                }
                // 如果不为空则放入请求头中，传递给下游微服务
                template.header("user-info", userId.toString());
            }
        };
    }
}
