package eu.righettod.poc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Start point for the application.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"eu.righettod.poc"})
public class Application {

    public static void main(String[] args) {
        System.setProperty("spring.config.name","application");
        SpringApplication.run(Application.class, args);
    }
}
