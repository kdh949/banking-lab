package lab.banking.notification.security

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableMethodSecurity
class NotificationSecurityConfig(
    @param:Value("\${banking-lab.security.enabled:true}")
    private val enabled: Boolean
) {
    @Bean
    fun notificationSecurityFilterChain(
        http: HttpSecurity,
        entryPoint: NotificationAuthenticationEntryPoint,
        jwtAuthenticationConverter: NotificationJwtAuthenticationConverter
    ): SecurityFilterChain {
        http.csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                it.authenticationEntryPoint(entryPoint)
                it.accessDeniedHandler(entryPoint)
            }
        if (!enabled) {
            http.authorizeHttpRequests { it.anyRequest().permitAll() }
            return http.build()
        }
        http.authorizeHttpRequests {
            it.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            it.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
            it.requestMatchers("/api/notifications/**").authenticated()
            it.anyRequest().permitAll()
        }
            .oauth2ResourceServer {
                it.authenticationEntryPoint(entryPoint)
                it.jwt { jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter) }
            }
        return http.build()
    }

    @Bean
    fun notificationJwtDecoder(
        objectMapper: ObjectMapper,
        @Value("\${banking-lab.security.jwt.jwks-uri:}") jwksUri: String,
        @Value("\${banking-lab.security.jwt.issuer:}") expectedIssuer: String,
        @Value("\${banking-lab.security.jwt.audience:}") expectedAudience: String,
        @Value("\${banking-lab.security.simulator-tokens-enabled:false}") simulatorTokensEnabled: Boolean,
        @Value("\${banking-lab.security.dev-simulator-token-enabled:false}") devSimulatorTokenEnabled: Boolean
    ): JwtDecoder =
        NotificationResourceServerJwtDecoder(
            objectMapper,
            jwksUri,
            expectedIssuer,
            expectedAudience,
            simulatorTokensEnabled,
            devSimulatorTokenEnabled
        )
}
