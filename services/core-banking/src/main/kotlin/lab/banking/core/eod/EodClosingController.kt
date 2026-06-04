package lab.banking.core.eod

import java.time.LocalDate
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/ops/eod")
class EodClosingController(
    private val eodClosingService: EodClosingService
) {
    @PostMapping("/close")
    fun requestClose(@RequestBody command: EodCloseCommand): ResponseEntity<EodCloseRequestResponse> {
        val response = eodClosingService.requestClose(command)
        return ResponseEntity.status(if (response.replayed) HttpStatus.OK else HttpStatus.CREATED).body(response)
    }

    @GetMapping("/{businessDate}")
    fun monitor(@PathVariable businessDate: LocalDate): EodClosingMonitorDto =
        eodClosingService.monitor(businessDate)
}
