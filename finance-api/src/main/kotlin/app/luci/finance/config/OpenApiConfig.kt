package app.luci.finance.config

import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI grouping bean for springdoc.
 * NOTE: The springdoc path configuration (`springdoc.api-docs.path`, `swagger-ui.enabled`)
 * lives in application.yml (T013), NOT here. This bean only defines the path grouping.
 * This resolves the F3 double-configuration issue. (FR-002a)
 */
@Configuration
class OpenApiConfig {

    @Bean
    fun skeletonOpenApi(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group("luci-skeleton")
            .pathsToMatch("/v1/**", "/health", "/.well-known/**")
            .build()
}
