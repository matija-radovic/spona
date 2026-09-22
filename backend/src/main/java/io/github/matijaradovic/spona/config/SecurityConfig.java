package io.github.matijaradovic.spona.config;

import java.time.Duration;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import io.github.matijaradovic.spona.auth.jwt.JwtAuthenticationFilter;
import io.github.matijaradovic.spona.auth.jwt.JwtService;
import io.github.matijaradovic.spona.common.error.ErrorCode;
import io.github.matijaradovic.spona.common.web.ProblemDetailWriter;
import io.github.matijaradovic.spona.config.props.AppProperties;
import io.github.matijaradovic.spona.user.UserRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_POSTS = {
            "/auth/register",
            "/auth/verify",
            "/auth/verify/resend",
            "/auth/login",
            "/auth/password-reset",
            "/auth/password-reset/confirm"
    };

    private static final String[] PUBLIC_GETS = {
            "/",
            "/users",
            "/users/*",
            "/organizations/*",
            "/actors",
            "/actors/*/followers",
            "/actors/*/following",
            "/actors/*/activity",
            "/actors/*/jobs",
            "/jobs",
            "/jobs/*",
            "/posts/*",
            "/posts/*/comments",
            "/posts/*/comments/*",
            "/posts/*/comments/*/responses",
            "/comments/*",
            "/comments/*/responses",
            "/reactions/*/*"
    };

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService, UserRepository users,
            ProblemDetailWriter problems) throws Exception {

        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, PUBLIC_GETS).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POSTS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint(problems))
                        .accessDeniedHandler(accessDeniedHandler(problems)))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, users, problems),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties app) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(app.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(false);
        config.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static AuthenticationEntryPoint authenticationEntryPoint(ProblemDetailWriter problems) {
        return (request, response, ex) -> problems.write(
                request, response, HttpStatus.UNAUTHORIZED, ErrorCode.SIGN_IN_REQUIRED);
    }

    private static AccessDeniedHandler accessDeniedHandler(ProblemDetailWriter problems) {
        return (request, response, ex) -> problems.write(
                request, response, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN);
    }
}
