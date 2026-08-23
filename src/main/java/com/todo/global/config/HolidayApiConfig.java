package com.todo.global.config;

import com.todo.domain.todo.recommendation.holiday.HolidayApiProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 공공데이터포털 특일정보 API 전용 클라이언트. {@code openAiRestClient}와 같은 구조다.
 * 추천 핸들러(비동기 스레드)에서만 쓰이지만, 타임아웃이 없으면 포털 장애 때 그 스레드가
 * 묶여 명령어 풀 전체가 밀린다.
 */
@Configuration
@EnableConfigurationProperties(HolidayApiProperties.class)
public class HolidayApiConfig {

    @Bean("holidayRestClient")
    public RestClient holidayRestClient(HolidayApiProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.readTimeout());
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
