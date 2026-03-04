package ai.agent.swagger.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = "ai.agent.swagger.repository")
public class MongoConfig {
}

