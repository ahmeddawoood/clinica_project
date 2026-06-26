package com.example.clinic.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${app.base-url:http://localhost:8082}")
    private String baseUrl;

    @Value("${openapi.production-url:https://clinic.example.com}")
    private String productionUrl;

    @Bean
    public OpenAPI clinicOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("MediClinic REST API")
                        .description("REST API pentru platforma de management al clinicii MediClinic, " +
                                "dezvoltata ca proiect de licenta la Universitatea Politehnica Timisoara. " +
                                "Acopera doctori, programari, statistici, recomandare AI, plati Stripe si timeline activitate. " +
                                "Autentificarea se face prin sesiune HTTP (form login la /login). " +
                                "Conturi demo: admin@clinic.com / admin123, doctor1@clinic.com / doctor123, mihai@email.com / patient123.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("MediClinic - Politehnica Timișoara")
                                .email("support@mediclinic.ro")
                                .url(baseUrl))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .externalDocs(new ExternalDocumentation()
                        .description("Cod sursă și documentație completă")
                        .url(baseUrl + "/actuator/info"))
                .servers(List.of(
                        new Server()
                                .url(baseUrl)
                                .description("Server local (dezvoltare)"),
                        new Server()
                                .url(productionUrl)
                                .description("Server producție (Docker)")
                ))
                .components(new Components()
                        .addSecuritySchemes("cookieAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")
                                .description("Sesiune HTTP Spring Security. Autentifică-te la `/login` pentru a obține cookie-ul de sesiune.")))
                .tags(List.of(
                        new Tag().name("REST API")
                                .description("Endpoint-uri JSON pentru integrare externă și testare Swagger"),
                        new Tag().name("Timeline")
                                .description("Jurnal activitate programări - creare, plată, confirmare, finalizare, anulare"),
                        new Tag().name("Queue & AI")
                                .description("Coadă inteligentă și recomandare doctor prin algoritm de scoring"),
                        new Tag().name("Plăți Stripe")
                                .description("Webhook Stripe pentru procesarea evenimentelor de plată și rambursare"),
                        new Tag().name("Doctori")
                                .description("Confirmare / anulare programări, consultații, coadă pacienți, rețete"),
                        new Tag().name("Pacienți")
                                .description("Programări, istoric medical, profil, rețete, facturi"),
                        new Tag().name("Admin")
                                .description("Statistici globale, gestionare utilizatori, audit log"),
                        new Tag().name("Autentificare")
                                .description("Înregistrare cont și autentificare (form-based)")
                ));
    }
}
