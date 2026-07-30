package lab.banking.payment.security

import lab.banking.payment.domain.PaymentDomainException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestClient

class PaymentCustomerAccountOwnershipVerifierTest {
    @Test
    fun `owned account passes through core banking customer account API`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val verifier = PaymentCustomerAccountOwnershipVerifier(true, "http://core-banking", builder)
        server.expect(requestTo("http://core-banking/api/customer/accounts/ACC-OWNED/detail?customerId=CUS-OWNED"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer customer-token"))
            .andRespond(withStatus(HttpStatus.OK))

        assertDoesNotThrow {
            verifier.requireOwned("CUS-OWNED", "ACC-OWNED", "Bearer customer-token")
        }
        server.verify()
    }

    @Test
    fun `core banking ownership denial is concealed as payment account not found`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val verifier = PaymentCustomerAccountOwnershipVerifier(true, "http://core-banking", builder)
        server.expect(requestTo("http://core-banking/api/customer/accounts/ACC-OTHER/detail?customerId=CUS-OWNED"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        val error = assertThrows(PaymentDomainException::class.java) {
            verifier.requireOwned("CUS-OWNED", "ACC-OTHER", "Bearer customer-token")
        }
        assertEquals("PAYMENT_DEBIT_ACCOUNT_NOT_FOUND", error.code)
        assertEquals(HttpStatus.NOT_FOUND, error.status)
        server.verify()
    }

    @Test
    fun `ownership check fails closed when core banking is unavailable`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val verifier = PaymentCustomerAccountOwnershipVerifier(true, "http://core-banking", builder)
        server.expect(requestTo("http://core-banking/api/customer/accounts/ACC-OWNED/detail?customerId=CUS-OWNED"))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE))

        val error = assertThrows(PaymentDomainException::class.java) {
            verifier.requireOwned("CUS-OWNED", "ACC-OWNED", "Bearer customer-token")
        }
        assertEquals("PAYMENT_ACCOUNT_OWNERSHIP_CHECK_UNAVAILABLE", error.code)
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.status)
        server.verify()
    }
}
