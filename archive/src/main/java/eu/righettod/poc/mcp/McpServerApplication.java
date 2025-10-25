package eu.righettod.poc.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * Start point for the MCP server.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"eu.righettod.poc.mcp"})
public class McpServerApplication {

    public static void main(String[] args) {
        System.setProperty("spring.config.name", "mcp-server");
        SpringApplication.run(McpServerApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider mcpServerTools(MCPServerTools mcpServerTools) {
        return MethodToolCallbackProvider.builder().toolObjects(mcpServerTools).build();
    }
}
