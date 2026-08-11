package lk.kavindu.clinic.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI clinicOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Clinic Appointment Booking API")
                        .version("v1")
                        .description("""
                                Appointment booking backend for a private clinic.

                                **Getting started**
                                1. `POST /api/v1/auth/register` — create a patient account
                                2. `POST /api/v1/auth/login` — get an access token
                                3. Click **Authorize** above and paste the access token
                                """)
                        .contact(new Contact().name("Kavindu").url("https://kavindu116.github.io"))
                        .license(new License().name("MIT")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME,
                        new SecurityScheme()
                                .name(SECURITY_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the accessToken from /auth/login (no 'Bearer ' prefix)")));
    }
}
