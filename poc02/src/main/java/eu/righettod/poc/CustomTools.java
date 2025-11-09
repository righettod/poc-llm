package eu.righettod.poc;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.output.structured.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Description("Tools that a model can leverage.")
public class CustomTools {


    private final Logger logger = LoggerFactory.getLogger(CustomTools.class);

    @Tool("Return a string with the health state of a system.")
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

    @Tool("Return a string with the technical information about the application.")
    public String getApplicationInformation() {
        logger.info("[TOOLS] Call getApplicationInformation().");
        return "VERSION: 2.1 - HOME: /app/v2.1 - APIKEY: 55fad0e3-a0a1-40b9-b923-3155ca275619";
    }
}
