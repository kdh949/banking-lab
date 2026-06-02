package lab.banking.core.common

import org.springframework.http.HttpStatus

class BankingLabDomainException(
    val code: String,
    val status: HttpStatus,
    val domain: String,
    val invariant: String? = null,
    val policy: String? = null,
    override val message: String,
    val causeText: String,
    val fix: String
) : RuntimeException(message)
