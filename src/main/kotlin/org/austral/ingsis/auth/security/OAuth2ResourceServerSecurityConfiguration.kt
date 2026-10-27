package org.austral.ingsis.auth.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod.GET
import org.springframework.security.config.Customizer.withDefaults
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

/**
 * JWT validation (signature, expiry, issuer and audience) is configured in application.yml
 * through spring.security.oauth2.resourceserver.jwt.*, so no custom JwtDecoder is needed.
 */
@Configuration
@EnableWebSecurity
class OAuth2ResourceServerSecurityConfiguration {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests {
                it
                    .requestMatchers(GET, "/").permitAll()
                    .requestMatchers(GET, "/actuator/health").permitAll()
                    // Spring maps each value of the token's "scope" claim to a "SCOPE_<value>" authority.
                    .requestMatchers(GET, "/scopedAuthenticate").hasAuthority("SCOPE_read:snippets")
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { it.jwt(withDefaults()) }
            // Bearer tokens make the API stateless: no HTTP session, no session cookie.
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // CSRF relies on the browser sending cookies automatically; a bearer token isn't sent that way.
            .csrf { it.disable() }
            // Enable and configure CORS when a browser frontend on another origin calls this API.
            .cors { it.disable() }
        return http.build()
    }
}
