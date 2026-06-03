package lab.banking.core.api

import lab.banking.core.config.BankingLabProperties
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController(
    private val properties: BankingLabProperties
) {
    @GetMapping("/health")
    fun health(): HealthResponse = HealthResponse(
        status = "ok",
        syntheticOnly = properties.syntheticOnly,
        auditHashChainValid = true,
        nodeReferenceRuntimeRetained = properties.nodeReferenceRuntimeRetained,
        migrationTarget = properties.migrationTarget
    )

    @GetMapping("/")
    fun root(): HealthResponse =
        health()

    @GetMapping("/robots.txt", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun robots(): String =
        """
        User-agent: *
        Disallow: /
        """.trimIndent()

    @GetMapping("/sitemap.xml", produces = [MediaType.APPLICATION_XML_VALUE])
    fun sitemap(): String =
        """<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" />"""
}
