package com.nexura.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${nexura.upload.dir}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Map static site requests to serve the frontend folder from working directory
        registry.addResourceHandler("/**")
                .addResourceLocations("file:./frontend/");

        // Ensure upload directory exists and map /uploads/** to it
        File uploadFolder = new File(uploadDir);
        if (!uploadFolder.exists()) {
            uploadFolder.mkdirs();
        }
        
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadFolder.getAbsolutePath() + "/");
    }
}
