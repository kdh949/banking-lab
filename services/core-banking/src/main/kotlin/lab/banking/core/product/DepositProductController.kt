package lab.banking.core.product

import java.time.LocalDate
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/products/deposits")
class DepositProductController(
    private val service: DepositProductService
) {
    @GetMapping
    fun list(@RequestParam(required = false) asOf: LocalDate?): ResponseEntity<DepositProductListResponse> =
        ResponseEntity.ok(service.listDepositProducts(asOf ?: LocalDate.now()))

    @GetMapping("/{productId}")
    fun detail(
        @PathVariable productId: String,
        @RequestParam(required = false) asOf: LocalDate?
    ): ResponseEntity<DepositProductDto> =
        ResponseEntity.ok(service.depositProduct(productId, asOf ?: LocalDate.now()))
}

@RestController
@RequestMapping("/api/staff/products/deposits")
class StaffDepositProductController(
    private val service: DepositProductService
) {
    @PostMapping("/{productId}/rate-change-requests")
    fun requestRateChange(
        @PathVariable productId: String,
        @RequestBody command: DepositRateChangeRequestCommand
    ): ResponseEntity<DepositRateChangeRequestResponse> =
        ResponseEntity.ok(service.requestRateChange(productId, command))
}

@RestController
@RequestMapping("/api/ops")
class OpsInterestController(
    private val service: DepositProductService
) {
    @PostMapping("/interest-accruals/run")
    fun runInterestAccrual(@RequestBody command: InterestAccrualRunCommand): ResponseEntity<InterestAccrualRunResponse> =
        ResponseEntity.ok(service.runInterestAccrual(command))

    @PostMapping("/interest-posting-batches")
    fun postInterestBatch(@RequestBody command: InterestPostingBatchCommand): ResponseEntity<InterestPostingBatchResponse> =
        ResponseEntity.ok(service.postInterestBatch(command))
}
