package com.dockerportal.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * 单镜像部署时同时提供前端静态资源与 SPA history 路由回退。
 * 开发模式下前端由 vite dev server 提供，本配置不影响 /api、/ws 接口。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 前端构建产物位置：默认 classpath，生产镜像通过 --dockerportal.web.static-location=file:/app/static/ 覆盖 */
    @Value("${dockerportal.web.static-location:classpath:/static/}")
    private String staticLocation;

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 站点根 "/" 不走下面那个资源处理器（它拿到的是相对路径 "."，会直接 500），
        // 显式 forward 到 index.html，让浏览器拿到 SPA 入口后由 Vue Router 分流到登录页/安装向导
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(staticLocation)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        // 空串 / "." 这类路径指向目录本身而不是某个文件，当文件返回会拿不到内容，
                        // 一律回退 index.html（根路径已由 addViewControllers 转发，这里接住空串与 "." 等变体）
                        boolean rootRequest = resourcePath == null || resourcePath.isBlank()
                                || ".".equals(resourcePath.trim()) || "/".equals(resourcePath.trim());
                        if (!rootRequest) {
                            Resource requested = location.createRelative(resourcePath);
                            if (requested.exists() && requested.isReadable()) {
                                return requested;
                            }
                        }
                        // 后端接口与 WebSocket 不做 index.html 回退，交回调度器返回 404
                        if (resourcePath != null && (resourcePath.startsWith("api/")
                                || resourcePath.startsWith("ws/")
                                || resourcePath.startsWith("h2-console"))) {
                            return null;
                        }
                        // 其余（Vue Router history 深链、刷新）一律回退到 index.html
                        Resource index = location.createRelative("index.html");
                        return index.exists() && index.isReadable() ? index : null;
                    }
                });
    }
}
