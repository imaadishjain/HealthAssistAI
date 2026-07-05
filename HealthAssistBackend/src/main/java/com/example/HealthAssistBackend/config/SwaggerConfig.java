package com.example.HealthAssistBackend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger / OpenAPI 3 configuration for HealthAssist AI Backend.
 * Swagger UI available at: http://localhost:8080/swagger-ui.html
 * OpenAPI JSON spec at:     http://localhost:8080/v3/api-docs
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI healthAssistOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("HealthAssist AI — Backend API")
                        .description("""
                                Smart Healthcare Assistance System powered by Spring AI.
                                
                                Features:
                                - Conversational Q&A (sync, streaming, agentic)
                                - RAG pipeline with hospital knowledge base
                                - Tool calling for appointments & tickets
                                - PDF SOP ingestion into vector store
                                - Guardrails: moderation, PII redaction, risk classification
                                - Full audit trail with provenance tracking
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("HealthAssist AI Team")
                                .email("healthassist@hospital.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Dev")))
                .tags(List.of(
                        new Tag().name("Chat").description("Conversational AI endpoints — sync, streaming & agentic"),
                        new Tag().name("Documents").description("PDF / document ingestion into the vector store"),
                        new Tag().name("Appointments").description("Doctor availability & appointment management"),
                        new Tag().name("Tickets").description("Facility / equipment incident tickets"),
                        new Tag().name("Audit").description("Workflow audit trail & provenance")
                ));
    }
}
