package app.luci.finance.api

import com.nimbusds.jose.jwk.JWKSet
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * GET /.well-known/jwks.json — publishes the M2M public key set (RFC 7517).
 * Unauthenticated. In prod, restricted to internal network via SecurityConfig.
 * (FR-019)
 */
@RestController
class JwksController(private val jwkSet: JWKSet) {

    @GetMapping("/.well-known/jwks.json", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun jwks(): String = jwkSet.toString()
}
