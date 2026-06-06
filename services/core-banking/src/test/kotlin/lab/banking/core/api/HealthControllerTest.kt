package lab.banking.core.api

import lab.banking.core.config.BankingLabProperties
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(HealthController::class)
@EnableConfigurationProperties(BankingLabProperties::class)
@Import(lab.banking.core.config.SecurityHeadersFilter::class, HealthControllerTest.PermitAllSecurityConfig::class)
@TestPropertySource(
    properties = [
        "banking-lab.synthetic-only=true",
        "banking-lab.node-reference-runtime-retained=true",
        "banking-lab.migration-target=kotlin-spring-boot",
        "banking-lab.security.enabled=false"
    ]
)
class HealthControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `health endpoint exposes synthetic boundary and retained Node reference`() {
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk)
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Cross-Origin-Resource-Policy", "same-origin"))
            .andExpect(header().string("Cross-Origin-Embedder-Policy", "require-corp"))
            .andExpect(header().string("Cross-Origin-Opener-Policy", "same-origin"))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.status").value("ok"))
            .andExpect(jsonPath("$.syntheticOnly").value(true))
            .andExpect(jsonPath("$.auditHashChainValid").value(true))
            .andExpect(jsonPath("$.nodeReferenceRuntimeRetained").value(true))
            .andExpect(jsonPath("$.migrationTarget").value("kotlin-spring-boot"))
    }

    @Test
    fun `root and crawler metadata endpoints avoid runtime error disclosure`() {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ok"))

        mockMvc.perform(get("/robots.txt"))
            .andExpect(status().isOk)
            .andExpect(content().string("User-agent: *\nDisallow: /"))

        mockMvc.perform(get("/sitemap.xml"))
            .andExpect(status().isOk)
            .andExpect(content().string("""<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" />"""))
    }

    @TestConfiguration
    class PermitAllSecurityConfig {
        @Bean
        fun testSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
            http
                .csrf { it.disable() }
                .authorizeHttpRequests { it.anyRequest().permitAll() }
                .build()
    }
}
