package lab.banking.core.api

import lab.banking.core.config.BankingLabProperties
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(HealthController::class)
@EnableConfigurationProperties(BankingLabProperties::class)
@TestPropertySource(
    properties = [
        "banking-lab.synthetic-only=true",
        "banking-lab.node-reference-runtime-retained=true",
        "banking-lab.migration-target=kotlin-spring-boot"
    ]
)
class HealthControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `health endpoint exposes synthetic boundary and retained Node reference`() {
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ok"))
            .andExpect(jsonPath("$.syntheticOnly").value(true))
            .andExpect(jsonPath("$.auditHashChainValid").value(true))
            .andExpect(jsonPath("$.nodeReferenceRuntimeRetained").value(true))
            .andExpect(jsonPath("$.migrationTarget").value("kotlin-spring-boot"))
    }
}
