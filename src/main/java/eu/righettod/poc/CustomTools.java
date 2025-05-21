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
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Custom functions that the LLM can used.
 */
@Description("Custom functions that the LLM can used")
public class CustomTools {

    private final Logger logger = LoggerFactory.getLogger(CustomTools.class);

    @Tool(name = "cvss score", value = "Returns the CVSS score for a CVE identifier")
    public String getCVSSScore(@P(value = "The CVE identifier for which the CVSS score should be returned", required = true) String cveIdentifier) throws Exception {
        String cveId = cveIdentifier.toUpperCase(Locale.ROOT).trim();
        logger.info("Use function to get the CVSS score of the CVE {}.", cveId);
        String score = "NA";
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder(new URI("https://cveawg.mitre.org/api/cve/" + cveId)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        Pattern pattern = Pattern.compile("\"baseScore\"\\s*:\\s*([0-9.]+)");
        Matcher matcher = pattern.matcher(response.body());
        if (matcher.find()) {
            score = matcher.group(1);
        }
        logger.info("CVE {} => {}.", cveId,score);
        return score;
    }
}
