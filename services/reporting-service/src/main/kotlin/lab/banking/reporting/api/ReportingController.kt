package lab.banking.reporting.api

import jakarta.servlet.http.HttpServletRequest
import lab.banking.reporting.domain.GenerateReportCommand
import lab.banking.reporting.domain.GenerateReportResponse
import lab.banking.reporting.domain.ReportArtifactExportResponse
import lab.banking.reporting.domain.ReportArtifactListResponse
import lab.banking.reporting.domain.ReportCatalogResponse
import lab.banking.reporting.domain.ReportingService
import lab.banking.reporting.security.ReportingAuthorizationFilter
import lab.banking.reporting.security.ReportingPrincipal
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class ReportingHealthResponse(
    val status: String = "ok",
    val service: String = "reporting-service",
    val syntheticOnly: Boolean = true,
    val migrationTarget: String = "kotlin-spring-boot"
)

@RestController
class ReportingController(
    private val service: ReportingService
) {
    @GetMapping("/health")
    fun health(): ReportingHealthResponse = ReportingHealthResponse()

    @GetMapping("/api/reports/catalog")
    fun catalog(
        @RequestParam(required = false) reason: String?,
        request: HttpServletRequest
    ): ReportCatalogResponse =
        service.catalog(reason, principal(request))

    @PostMapping("/api/reports/artifacts")
    fun generate(
        @RequestBody command: GenerateReportCommand,
        request: HttpServletRequest
    ): ResponseEntity<GenerateReportResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.generate(command, principal(request)))

    @GetMapping("/api/reports/artifacts")
    fun artifacts(
        @RequestParam(required = false) reason: String?,
        @RequestParam(required = false) reportType: String?,
        request: HttpServletRequest
    ): ReportArtifactListResponse =
        service.artifacts(reason, reportType, principal(request))

    @GetMapping("/api/reports/artifacts/{artifactId}/export")
    fun exportArtifact(
        @PathVariable artifactId: String,
        @RequestParam(required = false) reason: String?,
        request: HttpServletRequest
    ): ReportArtifactExportResponse =
        service.exportArtifact(artifactId, reason, principal(request))

    private fun principal(request: HttpServletRequest): ReportingPrincipal =
        request.getAttribute(ReportingAuthorizationFilter.PRINCIPAL_ATTRIBUTE) as? ReportingPrincipal
            ?: ReportingPrincipal("system", setOf("REPORTING_ANALYST"))
}
