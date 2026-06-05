package lab.banking.payment.core

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

class CoreBankingServiceTokenProviderTest {
    private var server: HttpServer? = null

    @AfterEach
    fun stopServer() {
        server?.stop(0)
    }

    @Test
    fun `static service token is used without client credentials call`() {
        val provider = CoreBankingServiceTokenProvider(
            staticServiceToken = "static-payment-service-token",
            tokenUrl = "http://127.0.0.1:1/realms/banking-lab/protocol/openid-connect/token",
            clientId = "payment-service-api",
            clientSecret = "payment-service-api-secret",
            refreshSkewSeconds = 30,
            restClientBuilder = RestClient.builder()
        )

        assertEquals("static-payment-service-token", provider.bearerToken())
    }

    @Test
    fun `client credentials token is fetched and cached`() {
        val requestCount = AtomicInteger(0)
        val tokenUrl = startTokenServer(requestCount)
        val provider = CoreBankingServiceTokenProvider(
            staticServiceToken = "",
            tokenUrl = tokenUrl,
            clientId = "payment-service-api",
            clientSecret = "payment-service-api-secret",
            refreshSkewSeconds = 30,
            restClientBuilder = RestClient.builder()
        )

        assertEquals("keycloak-payment-service-token", provider.bearerToken())
        assertEquals("keycloak-payment-service-token", provider.bearerToken())
        assertEquals(1, requestCount.get())
    }

    @Test
    fun `incomplete client credentials fail before unauthenticated core call`() {
        val provider = CoreBankingServiceTokenProvider(
            staticServiceToken = "",
            tokenUrl = "http://127.0.0.1:1/realms/banking-lab/protocol/openid-connect/token",
            clientId = "payment-service-api",
            clientSecret = "",
            refreshSkewSeconds = 30,
            restClientBuilder = RestClient.builder()
        )

        assertThrows(IllegalArgumentException::class.java) {
            provider.bearerToken()
        }
    }

    private fun startTokenServer(requestCount: AtomicInteger): String {
        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        httpServer.createContext("/realms/banking-lab/protocol/openid-connect/token") { exchange ->
            requestCount.incrementAndGet()
            val body = exchange.requestBody.bufferedReader().readText()
            assertEquals("POST", exchange.requestMethod)
            assertTrue(body.contains("grant_type=client_credentials"), body)
            assertTrue(body.contains("client_id=payment-service-api"), body)
            assertTrue(body.contains("client_secret=payment-service-api-secret"), body)
            val response = """{"access_token":"keycloak-payment-service-token","expires_in":120}"""
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }
        httpServer.start()
        server = httpServer
        return "http://127.0.0.1:${httpServer.address.port}/realms/banking-lab/protocol/openid-connect/token"
    }
}
