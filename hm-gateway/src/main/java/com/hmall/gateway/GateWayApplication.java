package com.hmall.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GateWayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GateWayApplication.class, args);
    }
    /*
      #核心机制：Spring Boot 的自动装配（Auto-configuration）
      #Spring Boot 启动时，会自动扫描所有依赖 JAR 包中的：
      #META-INF/spring.factories
      #文件，加载其中声明的自动配置类，并注册到 Spring 容器中
       只有写进 spring.factories 的配置类，才会被 Spring Boot 自动发现并加载。


      因为在pom文件中引入了hm-common模块，
      所以会自动装配hm-common的META-INF.spring.factories文件中的自动配置类到IOC容器中
    */

}
