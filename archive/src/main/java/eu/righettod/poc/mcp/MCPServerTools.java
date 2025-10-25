package eu.righettod.poc.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Tools exposed by the MCP Server
 */
@Service
public class MCPServerTools {

    private final Logger logger = LoggerFactory.getLogger(MCPServerTools.class);

    @Tool(name = "getWebContent", description = "Returns the content for a web page based on the URL provided.")
    public String getWebContent(@ToolParam(description = "The URL of the web page for which the content should be returned", required = true) String url) throws Exception {
        logger.info("[MCP_SERVER_TOOL] Use function to get the web content from '{}'.", url);
        String content = "No content found";
        if (url.startsWith("http://") || url.startsWith("https://")) {
            HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(20)).build();
            HttpRequest request = HttpRequest.newBuilder(new URI(url)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            content = response.body();
        }
        return content;
    }
}
