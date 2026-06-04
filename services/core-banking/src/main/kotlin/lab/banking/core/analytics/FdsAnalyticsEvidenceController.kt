package lab.banking.core.analytics

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/fds/analytics")
class FdsAnalyticsEvidenceController(
    private val service: FdsAnalyticsEvidenceService
) {
    @GetMapping
    fun evidence(@RequestParam(required = false) reason: String?): FdsAnalyticsEvidenceDto =
        service.evidence(reason)
}
