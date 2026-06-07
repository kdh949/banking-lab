package lab.banking.core.auth

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth/customer")
class CustomerAuthController(
    private val customerAuthService: CustomerAuthService
) {
    @PostMapping("/signup")
    fun signup(@RequestBody command: CustomerSignupCommand): ResponseEntity<CustomerAuthResponse> {
        val response = customerAuthService.signup(command)
        return ResponseEntity.status(if (response.replayed) HttpStatus.OK else HttpStatus.CREATED).body(response)
    }

    @PostMapping("/login")
    fun login(@RequestBody command: CustomerLoginCommand): CustomerAuthResponse =
        customerAuthService.login(command)
}
