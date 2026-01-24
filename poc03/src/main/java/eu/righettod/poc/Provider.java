package eu.righettod.poc;

import io.modelcontextprotocol.spec.McpSchema;
import org.springaicommunity.mcp.annotation.*;
import org.springaicommunity.mcp.context.McpSyncRequestContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Simple Tools + Resources + Prompts provider.
 */
@Component
public class Provider {

    @McpPrompt(name = "getCVEPrompt", description = "Generate a prompt for a CVE")
    public McpSchema.GetPromptResult getCVEPrompt(@McpArg(name = "cveId", description = "CVE identifier", required = true) String cveId) {
        return new McpSchema.GetPromptResult("Generate a prompt for a CVE", List.of(new McpSchema.PromptMessage(McpSchema.Role.ASSISTANT, new McpSchema.TextContent("Provide me details for the specified CVE '" + cveId + "'"))));
    }

    @McpResource(uri = "config://{cveId}", name = "getCVEData", description = "Provides content of a CVE.")
    public String getCVEData(McpSyncRequestContext context, String cveId) {
        context.info("CVE ID => " + cveId);
        return "Data for CVE ID " + cveId;
    }

    @McpTool(name = "getCVERating", description = "Get the CVSS rating a CVE.")
    public String getCVERating(McpSyncRequestContext context, @McpToolParam(description = "CVE identifier", required = true) String cveId, McpMeta meta) {
        context.info("CVE ID => " + cveId);
        return "Rating for CVE ID '" + cveId + "' is 10 (" + meta.meta() + ")";
    }
}
