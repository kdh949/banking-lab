package lab.banking.core.customer

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/customer/accounts")
class CustomerAccountController(
    private val customerAccountService: CustomerAccountService
) {
    @GetMapping
    fun accounts(@RequestParam customerId: String): CustomerAccountListResponse =
        customerAccountService.accounts(customerId)

    @GetMapping("/{accountId}/detail")
    fun detail(
        @PathVariable accountId: String,
        @RequestParam customerId: String
    ): CustomerAccountDetailDto =
        customerAccountService.detail(customerId, accountId)
}

@RestController
@RequestMapping("/api/customer/recipients")
class CustomerRecipientController(
    private val customerAccountService: CustomerAccountService
) {
    @GetMapping("/internal-account-lookup")
    fun internalAccountLookup(@RequestParam query: String): InternalRecipientLookupResponse =
        customerAccountService.internalRecipientLookup(query)
}
