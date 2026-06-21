package com.studylock.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // FIX: serve from correct path with trailing slash
        String location = uploadDir.endsWith("/") ? "file:" + uploadDir : "file:" + uploadDir + "/";
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
