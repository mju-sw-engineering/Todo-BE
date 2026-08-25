package com.todo;

import com.todo.domain.notification.message.NotificationMessageProperties;
import com.todo.global.config.AppleProperties;
import com.todo.global.file.config.OrphanCleanupProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.todo.global.ai.OpenAiProperties;
import com.todo.domain.todo.recommendation.TodoRecommendationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.TimeZone;

@EnableJpaAuditing
@EnableConfigurationProperties({NotificationMessageProperties.class, AppleProperties.class, OpenAiProperties.class, TodoRecommendationProperties.class, OrphanCleanupProperties.class})
@SpringBootApplication
public class TodoApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        SpringApplication.run(TodoApplication.class, args);
    }
}
