package com.todo;

import com.todo.domain.notification.message.NotificationMessageProperties;
import com.todo.global.config.AppleProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.todo.global.ai.OpenAiProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.TimeZone;

@EnableJpaAuditing
@EnableConfigurationProperties({NotificationMessageProperties.class, AppleProperties.class, OpenAiProperties.class})
@SpringBootApplication
public class TodoApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        SpringApplication.run(TodoApplication.class, args);
    }
}
