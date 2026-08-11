package com.todo.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.todo.domain.auth.service.AuthService;
import com.todo.global.jwt.JwtAuthenticationFilter;
import com.todo.global.jwt.JwtUtil;
import com.todo.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final AuthService authService;
    private final CorsConfigurationSource corsConfigurationSource;
    private final ObjectMapper objectMapper;

    @Value("${swagger.auth.username}")
    private String swaggerUsername;

    @Value("${swagger.auth.password}")
    private String swaggerPassword;

    @Bean
    @Order(1)
    public SecurityFilterChain swaggerFilterChain(HttpSecurity http) throws Exception {
        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager(
                User.withUsername(swaggerUsername)
                        .password("{noop}" + swaggerPassword)
                        .roles("SWAGGER")
                        .build()
        );

        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(manager);

        return http
                .securityMatcher("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html")
                .authenticationProvider(provider)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/ws/**", "/actuator/**", "/error"))
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                SecurityErrorResponseWriter.write(
                                        objectMapper,
                                        response,
                                        HttpServletResponse.SC_UNAUTHORIZED,
                                        "로그인이 필요합니다"
                                ))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                SecurityErrorResponseWriter.write(
                                        objectMapper,
                                        response,
                                        HttpServletResponse.SC_FORBIDDEN,
                                        "접근 권한이 없습니다"
                                ))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/signup", "/api/auth/login", "/api/auth/refresh", "/api/auth/email/**", "/api/auth/apple/**").permitAll()
                        .requestMatchers("/api/files/presigned-upload").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/.well-known/apple-app-site-association").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/terms").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtUtil, authService),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}

class SecurityErrorResponseWriter {

    private SecurityErrorResponseWriter() {
    }

    static void write(ObjectMapper objectMapper, HttpServletResponse response, int status, String message)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(message));
    }
}
