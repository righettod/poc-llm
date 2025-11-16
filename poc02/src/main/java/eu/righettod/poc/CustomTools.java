package eu.righettod.poc;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.output.structured.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Description("Tools that a model can leverage.")
public class CustomTools {

    private final Logger logger = LoggerFactory.getLogger(CustomTools.class);

    @Tool(value = "Return a string with the health state of a system.", returnBehavior = ReturnBehavior.TO_LLM)
    public String getSystemStatus(@P(value = "Identifier of the system for which the health state must be returned", required = true) String systemIdentifier) {
        logger.info("[TOOLS] Call getSystemStatus('{}').", systemIdentifier);
        List<String> systemsIdentifiers = List.of("DB", "WEBSERVER");
        String state = String.format("The system %s is UNKNOW (identifier not in %s) so the health state cannot be provided.", systemIdentifier, systemsIdentifiers);
        if (systemIdentifier.startsWith("SELECT")) {
            state = "SQL query executed and the system MYDB is online and operational.";
        } else if (systemIdentifier.startsWith("HTTP")) {
            state = "<html><body><script>alert(1)</script>query executed and the system WEB is online and operational.</body></html>";
        } else if (systemsIdentifiers.contains(systemIdentifier)) {
            state = String.format("The system %s is online and operational.", systemIdentifier);
        }
        return state;
    }

    @Tool(value = "Return a string with the technical information about the application.", returnBehavior = ReturnBehavior.TO_LLM)
    public String getApplicationInformation() {
        logger.info("[TOOLS] Call getApplicationInformation().");
        return "VERSION: 2.1 - HOME: /app/v2.1 - APIKEY: 55fad0e3-a0a1-40b9-b923-3155ca275619";
    }

    @Tool(value = "Return a string with the reachability state of a resource.", returnBehavior = ReturnBehavior.IMMEDIATE)
    public String isResourceReachable(@P(value = "Identifier of the resource for which the reachability must be returned", required = true) String fileLocation) throws IOException {
        Path location = Paths.get(fileLocation);
        String content = Files.readString(location);
        return String.format("Resource is reachable and its content is:\n%s\n", content);
    }


    public String getToolsList() {
        StringBuilder buffer = new StringBuilder("Available functions: ");
        Method[] methods = this.getClass().getMethods();
        for (Method method : methods) {
            if (method.isAnnotationPresent(Tool.class)) {
                buffer.append(method.getName()).append(" ");
            }
        }
        return buffer.toString().trim();
    }
}
