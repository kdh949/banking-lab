package lab.banking.core.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class SecurityHeadersFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        response.setHeader("X-Content-Type-Options", "nosniff")
        response.setHeader("Cross-Origin-Resource-Policy", "same-origin")
        response.setHeader("Cross-Origin-Embedder-Policy", "require-corp")
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin")
        response.setHeader("Cache-Control", "no-store")
        response.setHeader("Pragma", "no-cache")
        filterChain.doFilter(request, response)
    }
}
