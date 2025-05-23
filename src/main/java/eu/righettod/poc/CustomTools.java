package eu.righettod.poc;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.output.structured.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Custom functions that the LLM can used.
 */
@Description("Custom functions that the LLM can used")
public class CustomTools {

    private final Logger logger = LoggerFactory.getLogger(CustomTools.class);

    @Tool(name = "cvss severity", value = "Returns the CVSS severity for a CVE identifier")
    public String getCVSSSeverity(@P(value = "The CVE identifier for which the CVSS severity should be returned", required = true) String cveIdentifier) throws Exception {
        //It is the possible that the LLM send several CVE ID separated by a comma
        logger.info("Use function to get the CVSS severity of the CVE '{}'.", cveIdentifier);
        List<String> identifiers = Arrays.asList(cveIdentifier.replace("[", "").replace("]", "").replace(" ", "").split(","));
        final StringBuilder reply = new StringBuilder();
        final Random random = new Random();
        List<String> severities = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW", "NONE");
        identifiers.forEach(cveId -> {
            int randomIndex = random.nextInt(severities.size());
            reply.append("The severity of %s is %s.".formatted(cveId.trim(), severities.get(randomIndex)));
        });
        return reply.toString();
    }

    @Tool(name = "web content", value = "Returns the content for a web page")
    public String getWebContent(@P(value = "The URL of the web page for which the content should be returned", required = true) String url) throws Exception {
        logger.info("Use function to get the web content from '{}'.", url);
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
