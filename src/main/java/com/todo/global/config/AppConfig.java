package com.todo.global.config;

import com.todo.global.ai.OpenAiProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class AppConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    /**
     * OpenAI 전용 클라이언트. 호출부가 폴러라 타임아웃이 없으면 스레드가 물려 분석 큐 전체가
     * 밀린다. 읽기 타임아웃을 애플(5s)보다 길게 두는 것은 모델 추론이 그만큼 걸리기 때문이다.
     */
    @Bean("openAiRestClient")
    public RestClient openAiRestClient(OpenAiProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.readTimeout());
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    @Bean("appleRestClient")
    public RestClient appleRestClient(
            @Value("${apple.connect-timeout:3s}") Duration connectTimeout,
            @Value("${apple.read-timeout:5s}") Duration readTimeout
    ) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(connectTimeout)
                .withReadTimeout(readTimeout);
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
