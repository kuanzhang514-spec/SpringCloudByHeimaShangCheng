package com.hmall.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 需要将属性配置类注册成为Spring容器中的Bean。
 * 要么在属性配置类上加@Component,
 * 要么在其他Bean组件上加@EnableConfigurationProperties引入属性配置类  -->本次选择的是这种方式
 */
@Data
@ConfigurationProperties(prefix = "hm.auth")
public class AuthProperties {
    private List<String> includePaths;
    private List<String> excludePaths;
}
