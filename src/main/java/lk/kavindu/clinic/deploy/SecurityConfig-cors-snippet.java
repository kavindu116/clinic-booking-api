// =====================================================================
// SecurityConfig.java eke corsConfigurationSource() method eka
// MEKEN REPLACE KARANNA. Ekai class eke uda me field eka add karanna.
// =====================================================================

    // Class eke uda, jwtAuthFilter etc. ekka:
    @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origins:}")
    private String allowedOrigins;

    /**
     * Dev ekedi localhost eke ona port ekak. Production ekedi
     * app.cors.allowed-origins eken enna one, ekath hisswa nam
     * KISIMA origin ekak ganne naa.
     *
     * Kalin "http://localhost:*" hardcode karala thibba -- eka
     * production ekata giyoth kenekta localhost eke duwana page
     * ekakin ape API ekata credentials ekka request yawanna puluwan.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            // Dev default. Production eke me property eka set wenawa.
            config.setAllowedOriginPatterns(List.of("http://localhost:*"));
        } else {
            config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        }

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id"));
        config.setExposedHeaders(List.of("X-Correlation-Id", "X-RateLimit-Remaining"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
