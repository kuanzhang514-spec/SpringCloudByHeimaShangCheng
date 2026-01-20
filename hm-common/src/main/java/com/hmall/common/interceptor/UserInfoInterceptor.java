package com.hmall.common.interceptor;

import cn.hutool.core.util.StrUtil;
import com.hmall.common.utils.UserContext;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 这是springMVC的拦截器，是网关转发路由的下一层(一定是),也是openFeign发起调用首先经过的地方
 * 这个拦截器只做一件事：获取当前登录用户的信息并存入ThreadLocal,不做任何的拦截，只管放行就好
 * request头->threadLocal
 * 只要有请求发往微服务，就一定会经过这个拦截器（openFeign发起的也算）
 */
public class UserInfoInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //获取登录用户（从request的请求头中取）
        String userInfo = request.getHeader("user-info");
        if (!StrUtil.isNotBlank(userInfo)) {
            return true;
        }
        //存入threadLocal中
        UserContext.setUser(Long.valueOf(userInfo));
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable ModelAndView modelAndView) throws Exception {
        //移除threadLocal中的用户数据
        UserContext.removeUser();
    }
}
