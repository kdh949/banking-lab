package lab.banking.core.observability

import io.micrometer.tracing.Span
import io.micrometer.tracing.Tracer
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class TraceLogCorrelationFilter(
    private val tracerProvider: ObjectProvider<Tracer>
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(TraceLogCorrelationFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val tracer = tracerProvider.ifAvailable
        if (tracer == null) {
            filterChain.doFilter(request, response)
            logAccess(request, response, null)
            return
        }

        val currentSpan = tracer.currentSpan()
        if (currentSpan != null) {
            filterChain.doFilter(request, response)
            logAccess(request, response, currentSpan)
            return
        }

        val span = tracer.nextSpan()
            .name("banking-lab.http ${request.method} ${request.requestURI}")
            .start()
        try {
            tracer.withSpan(span).use {
                filterChain.doFilter(request, response)
                logAccess(request, response, span)
            }
        } catch (ex: Throwable) {
            span.error(ex)
            throw ex
        } finally {
            span.end()
        }
    }

    private fun logAccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        span: Span?
    ) {
        val context = span?.context()
        log.info(
            "observability.access method={} path={} status={} requestId={} traceId={} spanId={} syntheticOnly=true",
            request.method,
            request.requestURI,
            response.status,
            request.getHeader("x-request-id") ?: "generated",
            context?.traceId() ?: "unavailable",
            context?.spanId() ?: "unavailable"
        )
    }
}
