package lab.banking.core.audit

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/audit/events")
class AuditEventController(
    private val auditEventService: AuditEventService
) {
    @GetMapping
    fun list(): AuditEventListResponse =
        auditEventService.list()
}
