package lab.banking.reporting

import lab.banking.reporting.eventing.ReportingDomainEventPublisherProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(ReportingDomainEventPublisherProperties::class)
class ReportingServiceApplication

fun main(args: Array<String>) {
    runApplication<ReportingServiceApplication>(*args)
}
