package com.dontdelay.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            HandlerMappingIntrospector introspector
    ) throws Exception {
        MvcRequestMatcher.Builder mvc = new MvcRequestMatcher.Builder(introspector);

        http
            .cors(cors -> {})
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    // AntPath: Spring Security 6 MVC /** 패턴은 signup 등에 매칭 안 될 수 있음
                    .requestMatchers(new AntPathRequestMatcher("/api/auth/**")).permitAll()
                    .requestMatchers(
                            new AntPathRequestMatcher("/api/health"),
                            new AntPathRequestMatcher("/actuator/health"),
                            new AntPathRequestMatcher("/h2-console/**")
                    ).permitAll()
                    .requestMatchers(mvc.pattern("/api/exam/**")).authenticated()
                    .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) ->
                        writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다."))
                .accessDeniedHandler((request, response, accessDeniedException) ->
                        writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다."))
            )
            .sessionManagement(session -> session.sessionCreationPolicy(
                    org.springframework.security.config.http.SessionCreationPolicy.IF_REQUIRED))
            .headers(headers -> headers.frameOptions(frame -> frame.disable()));

        return http.build();
    }

    private static void writeJsonError(
            HttpServletResponse response,
            int status,
            String error,
            String message
    ) throws java.io.IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String body = String.format("{\"error\":\"%s\",\"message\":\"%s\"}", error, message);
        response.getWriter().write(body);
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
