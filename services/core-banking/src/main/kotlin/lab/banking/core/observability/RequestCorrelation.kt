package lab.banking.core.observability

import jakarta.servlet.http.HttpServletRequest
import java.util.UUID
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

object RequestCorrelation {
    const val REQUEST_ID_ATTRIBUTE = "banking.lab.safe-request-id"

    private val requestIdPattern = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
    private val traceparentPattern = Regex("^00-([0-9a-f]{32})-([0-9a-f]{16})-(00|01)$")

    fun installRequestId(request: HttpServletRequest): String {
        val requestId = safeRequestId(request.getHeader("x-request-id"))
            ?: "REQ-${UUID.randomUUID().toString().uppercase()}"
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId)
        return requestId
    }

    fun currentRequestId(): String? {
        val request = currentRequest() ?: return null
        return (request.getAttribute(REQUEST_ID_ATTRIBUTE) as? String)
            ?: safeRequestId(request.getHeader("x-request-id"))
    }

    fun currentTraceId(): String? =
        safeTraceparent(currentRequest()?.getHeader("traceparent"))
            ?.let { traceparentPattern.matchEntire(it)?.groupValues?.get(1) }

    fun safeRequestId(value: String?): String? =
        value?.trim()?.takeIf { requestIdPattern.matches(it) }

    fun safeTraceparent(value: String?): String? {
        val normalized = value?.trim()?.lowercase() ?: return null
        val match = traceparentPattern.matchEntire(normalized) ?: return null
        val traceId = match.groupValues[1]
        val parentId = match.groupValues[2]
        if (traceId.all { it == '0' } || parentId.all { it == '0' }) {
            return null
        }
        return normalized
    }

    private fun currentRequest(): HttpServletRequest? =
        (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
}
