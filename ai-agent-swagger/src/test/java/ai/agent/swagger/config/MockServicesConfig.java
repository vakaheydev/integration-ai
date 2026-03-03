package ai.agent.swagger.config;

import ai.agent.swagger.security.*;
import ai.agent.swagger.service.*;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * Тестовая конфигурация: заменяет все бины, требующие внешних подключений
 * (ChromaDB, Ollama, LangChain4j, MongoDB), на Mockito-моки.
 * Также предоставляет моки для цепочки Spring Security зависимостей.
 * Подключается через @Import в контроллерных тестах.
 */
@TestConfiguration
@Import(SecurityConfig.class)
public class MockServicesConfig {

    // --- MongoDB ---

    @Bean
    public MongoDatabaseFactory mongoDatabaseFactory() {
        return Mockito.mock(MongoDatabaseFactory.class);
    }

    @Bean
    @SuppressWarnings("unchecked")
    public MongoTemplate mongoTemplate() {
        MongoMappingContext mappingContext = Mockito.mock(MongoMappingContext.class);
        Mockito.when(mappingContext.getPersistentEntity(Mockito.any(Class.class)))
                .thenReturn(Mockito.mock(MongoPersistentEntity.class, Mockito.RETURNS_DEEP_STUBS));
        MongoConverter converter = Mockito.mock(MongoConverter.class);
        Mockito.doReturn(mappingContext).when(converter).getMappingContext();
        MongoTemplate template = Mockito.mock(MongoTemplate.class);
        Mockito.when(template.getConverter()).thenReturn(converter);
        return template;
    }

    // --- AI / Vector / Storage ---

    @Bean
    public AiChatService aiChatService() {
        return Mockito.mock(AiChatService.class);
    }

    @Bean
    public EmbeddingService embeddingService() {
        return Mockito.mock(EmbeddingService.class);
    }

    @Bean
    public VectorStorageService vectorStorageService() {
        return Mockito.mock(VectorStorageService.class);
    }

    @Bean
    public DocumentStorageService documentStorageService() {
        return Mockito.mock(DocumentStorageService.class);
    }

    @Bean
    public PromptBuilderService promptBuilderService() {
        return Mockito.mock(PromptBuilderService.class);
    }

    // --- DataInitializer ---

    @Bean
    public DataInitializer dataInitializer() {
        return Mockito.mock(DataInitializer.class);
    }

    // --- Spring Security зависимости для SecurityConfig ---

    @Bean
    public UserDetailsService userDetailsService() {
        return Mockito.mock(UserDetailsService.class);
    }

    @Bean
    public JwtFilter jwtFilter() {
        return Mockito.mock(JwtFilter.class);
    }

    @Bean
    public JwtAuthEntryPoint jwtAuthEntryPoint() {
        return Mockito.mock(JwtAuthEntryPoint.class);
    }

    @Bean
    public JwtAccessDeniedHandler jwtAccessDeniedHandler() {
        return Mockito.mock(JwtAccessDeniedHandler.class);
    }


}
