package com.globalisor.backend.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;

@Configuration
@EnableMethodSecurity
public class WebSecurityConfig {
    @Autowired
    UserDetailsServiceImpl userDetailsService;

    @Autowired
    private AuthEntryPointJwt unauthorizedHandler;

    @Bean
    public AuthTokenFilter authenticationJwtTokenFilter() {
        return new AuthTokenFilter();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);

        authProvider.setPasswordEncoder(passwordEncoder());

        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("*")); // Allow all origins or specify "http://localhost:5173"
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("authorization", "content-type", "x-auth-token"));
        configuration.setExposedHeaders(Arrays.asList("x-auth-token"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .headers(headers -> headers.frameOptions(frame -> frame.disable()))
                .exceptionHandling(exception -> exception.authenticationEntryPoint(unauthorizedHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> 
                    auth.requestMatchers("/api/auth", "/api/auth/**").permitAll()
                        .requestMatchers("/api/dashboard", "/api/dashboard/**").permitAll()
                        .requestMatchers("/api/clients", "/api/clients/**").permitAll()
                        .requestMatchers("/api/services", "/api/services/**").permitAll()
                        .requestMatchers("/api/applications", "/api/applications/**").permitAll()
                        .requestMatchers("/api/admin", "/api/admin/**").permitAll()
                        .requestMatchers("/api/blogs", "/api/blogs/**").permitAll()
                        .requestMatchers("/api/static-content", "/api/static-content/**").permitAll()
                        .requestMatchers("/api/countries", "/api/countries/**").permitAll()
                        .requestMatchers("/api/kyc", "/api/kyc/**").permitAll()
                        .requestMatchers("/api/compliance", "/api/compliance/**").permitAll()
                        .requestMatchers("/api/compliance-events", "/api/compliance-events/**").permitAll()
                        .requestMatchers("/api/documents", "/api/documents/**").permitAll()
                        .requestMatchers("/api/notifications", "/api/notifications/**").permitAll()
                        .requestMatchers("/api/messages", "/api/messages/**", "/api/ws/**").permitAll()
                        .requestMatchers("/api/reports", "/api/reports/**").permitAll()
                        .requestMatchers("/api/catalog", "/api/catalog/**").permitAll()
                        .requestMatchers("/api/prereg-sections", "/api/prereg-sections/**").permitAll()
                        .requestMatchers("/api/ssic-activities", "/api/ssic-activities/**").permitAll()
                        .requestMatchers("/api/requirements", "/api/requirements/**").permitAll()
                        .requestMatchers("/api/name-check", "/api/name-check/**").permitAll()
                        .requestMatchers("/api/hr", "/api/hr/**").permitAll()
                        .requestMatchers("/api/onboarding", "/api/onboarding/**").permitAll()
                        .requestMatchers("/api/ocr", "/api/ocr/**").permitAll()
                        .requestMatchers("/api/onboarding-config", "/api/onboarding-config/**").permitAll()
                        .requestMatchers("/api/admin/intelligence", "/api/admin/intelligence/**").permitAll()
                        .requestMatchers("/api/tasks", "/api/tasks/**").permitAll()
                        .requestMatchers("/api/staff", "/api/staff/**").permitAll()
                        .requestMatchers("/api/integrations", "/api/integrations/**").permitAll()
                        .requestMatchers("/api/bizfile", "/api/bizfile/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated()
                );

        http.authenticationProvider(authenticationProvider());

        http.addFilterBefore(authenticationJwtTokenFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
