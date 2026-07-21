package com.todo.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;

@Configuration
public class AppConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public RestClientCustomizer restClientTimeoutCustomizer(
            @Value("${ai.server.connect-timeout:3s}") Duration connectTimeout,
            @Value("${ai.server.read-timeout:15s}") Duration readTimeout
    ) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(connectTimeout)
                .withReadTimeout(readTimeout);
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);
        return builder -> builder.requestFactory(requestFactory);
    }
}
