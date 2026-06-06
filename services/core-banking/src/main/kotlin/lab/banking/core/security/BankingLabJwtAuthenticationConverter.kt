package lab.banking.core.security

import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

@Component
class BankingLabJwtAuthenticationConverter : Converter<Jwt, AbstractAuthenticationToken> {
    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        val roles = BankingLabJwtClaims.rolesFromClaims(jwt.claims)
        val authorities = roles
            .flatMap { role ->
                listOf(
                    SimpleGrantedAuthority("ROLE_$role"),
                    SimpleGrantedAuthority("SCOPE_$role")
                )
            }
            .toSet()
        val subject = BankingLabJwtClaims.subjectFromClaims(jwt.claims) ?: jwt.subject
        return JwtAuthenticationToken(jwt, authorities, subject)
    }
}
