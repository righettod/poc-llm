package eu.righettod.poc;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Start point for the application.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"eu.righettod.poc"})
public class Application {

    @Value("${spring.ai.mcp.server.protocol}")
    private String protocol;

    public static void main(String[] args) {
        System.setProperty("spring.config.name", "application");
        SpringApplication.run(Application.class, args);
    }

    @PostConstruct
    public void init() {
        System.out.printf("[i] Protocol enabled: %s\n", protocol);
    }
}
