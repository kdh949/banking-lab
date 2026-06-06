package lab.banking.core.security

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class AuthorizationMetrics(
    private val registry: MeterRegistry
) {
    fun recordDenied(code: String, policy: String, routeFamily: String) {
        Counter
            .builder("banking.lab.authorization.denied.count")
            .description("Authorization denials emitted as structured errors and audit events")
            .tag("code", code)
            .tag("policy", policy)
            .tag("route_family", routeFamily)
            .register(registry)
            .increment()
    }
}
