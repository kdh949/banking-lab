package lab.banking.core.security

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableMethodSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class SecurityConfig(
    @param:Value("\${banking-lab.security.enabled:true}")
    private val enabled: Boolean
) {
    @Bean
    fun bankingLabSecurityFilterChain(
        http: HttpSecurity,
        entryPoint: BankingLabAuthenticationEntryPoint,
        jwtAuthenticationConverter: BankingLabJwtAuthenticationConverter
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
            it.requestMatchers("/", "/health", "/api/health", "/robots.txt", "/sitemap.xml", "/actuator/health", "/actuator/health/**").permitAll()
            it.requestMatchers("/api/**").authenticated()
            it.anyRequest().permitAll()
        }
            .oauth2ResourceServer {
                it.authenticationEntryPoint(entryPoint)
                it.jwt { jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter) }
            }
        return http.build()
    }

    @Bean
    fun bankingLabJwtDecoder(
        objectMapper: ObjectMapper,
        @Value("\${banking-lab.security.jwt.jwks-uri:}") jwksUri: String,
        @Value("\${banking-lab.security.jwt.issuer:}") expectedIssuer: String,
        @Value("\${banking-lab.security.jwt.audience:}") expectedAudience: String,
        @Value("\${banking-lab.security.simulator-tokens-enabled:false}") simulatorTokensEnabled: Boolean,
        @Value("\${banking-lab.security.dev-simulator-token-enabled:false}") devSimulatorTokenEnabled: Boolean
    ): JwtDecoder =
        BankingLabResourceServerJwtDecoder(
            objectMapper = objectMapper,
            jwksUri = jwksUri,
            expectedIssuer = expectedIssuer,
            expectedAudience = expectedAudience,
            simulatorTokensEnabled = simulatorTokensEnabled,
            devSimulatorTokenEnabled = devSimulatorTokenEnabled
        )

    @Bean
    fun bankingLabPasswordEncoder(): PasswordEncoder =
        PasswordEncoderFactories.createDelegatingPasswordEncoder()
}
