package app.luci.finance.api

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

/**
 * POST /v1/internal/whoami — echoes verified M2M JWT claims back to the caller.
 * No business logic. Exists solely to validate the M2M auth seam. (FR-020)
 */
@RestController
class WhoamiController {

    data class WhoamiResponse(
        val subject: String,
        val user_id: String,
        val intent: String,
        val trace_id: String,
    )

    @PostMapping("/v1/internal/whoami")
    fun whoami(@AuthenticationPrincipal jwt: Jwt): WhoamiResponse {
        return WhoamiResponse(
            subject = jwt.subject ?: "",
            user_id = jwt.getClaimAsString("user_id") ?: "",
            intent = jwt.getClaimAsString("intent") ?: "",
            trace_id = jwt.getClaimAsString("trace_id") ?: "",
        )
    }
}
