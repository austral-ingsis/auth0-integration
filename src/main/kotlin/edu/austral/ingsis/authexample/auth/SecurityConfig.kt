package edu.austral.ingsis.authexample.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod.GET
import org.springframework.http.HttpMethod.POST
import org.springframework.security.config.Customizer.withDefaults
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity.CsrfSpec
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoders
import org.springframework.security.web.server.SecurityWebFilterChain

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
class SecurityConfig(
    @Value("\${auth0.audience}")
    val audience: String,
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    val issuer: String,
) {

    @Bean
    fun securityWebFilterChain(serverSecurity: ServerHttpSecurity): SecurityWebFilterChain = serverSecurity
        .authorizeExchange {
            it
                .pathMatchers("/health/**").permitAll()
                .pathMatchers(GET, "/snippets").hasAuthority("SCOPE_read:snippets")
                .pathMatchers(GET, "/snippets/*").hasAuthority("SCOPE_read:snippets")
                .pathMatchers(POST, "/snippets").hasAuthority("SCOPE_write:snippets")
                .anyExchange().denyAll()
        }
        .oauth2ResourceServer { it.jwt(withDefaults()) }
        .cors(withDefaults())
        .csrf(CsrfSpec::disable)
        .build()

    @Bean
    fun jwtDecoder(): ReactiveJwtDecoder {
        val audienceValidator: OAuth2TokenValidator<Jwt> = AudienceValidator(audience)
        val withIssuer: OAuth2TokenValidator<Jwt> = JwtValidators.createDefaultWithIssuer(issuer)
        val withAudience: OAuth2TokenValidator<Jwt> = DelegatingOAuth2TokenValidator(withIssuer, audienceValidator)

        val jwtDecoder = ReactiveJwtDecoders.fromOidcIssuerLocation(issuer) as NimbusReactiveJwtDecoder
        jwtDecoder.setJwtValidator(withAudience)

        return jwtDecoder
    }
}
