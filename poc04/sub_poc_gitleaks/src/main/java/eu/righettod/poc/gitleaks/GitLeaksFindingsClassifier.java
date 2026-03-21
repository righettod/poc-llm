package eu.righettod.poc.gitleaks;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import eu.righettod.poc.AgentEventTracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;

import static eu.righettod.poc.Constants.*;

/**
 * Entry point to run the POC.
 * @see "https://docs.langchain4j.dev/tutorials/agents#loop-workflow"
 */
public class GitLeaksFindingsClassifier {

    private static final int SUPERVISOR_AGENT_MAX_ITERATIONS = 5;

    private static final Logger LOGGER = LoggerFactory.getLogger(GitLeaksFindingsClassifier.class);

    static void main(String[] args) throws Exception {
        //Create the model ran through Ollama
        ChatModel model = OllamaChatModel.builder()
                .baseUrl(OLLAMA_BASE_URL)
                .temperature(MODEL_TEMPERATURE)
                .timeout(Duration.ofSeconds(OLLAMA_RESPONSE_TIMEOUT))
                .logRequests(DEBUG)
                .logResponses(DEBUG)
                .modelName(MODEL_NAME)
                .build();

        //Create the secret identifier agent
        AgentEventTracer agentEventTracer = new AgentEventTracer();
        SecretIdentifierAgentTools secretIdentifierAgentTools = new SecretIdentifierAgentTools();
        SecretIdentifierAgent secretIdentifierAgent = AgenticServices.agentBuilder(SecretIdentifierAgent.class)
                .chatModel(model)
                .tools(secretIdentifierAgentTools)
                .outputKey("secretType")
                .name("SECRET-IDENTIFIER")
                .listener(agentEventTracer)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .build();

        //Build the analysis loop via a supervisor agent
        UntypedAgent supervisorAgent = AgenticServices
                .loopBuilder().subAgents(secretIdentifierAgent)
                .outputKey("secretType")
                .name("SUPERVISOR")
                .listener(agentEventTracer)
                .exitCondition(agenticScope -> {
                    String secretType = (String) agenticScope.readState("secretType");
                    boolean mustExit = (!"unknown".equalsIgnoreCase(secretType));
                    GitLeaksFindingsClassifier.LOGGER.info("[SUPERVISOR:exitCondition] Checking exit condition with secret type = '{}' => Exit ? {}.", secretType, mustExit);
                    return mustExit;
                })
                .maxIterations(SUPERVISOR_AGENT_MAX_ITERATIONS)
                .build();

        //Create initial parameter: Because we use an untyped agent, we need to pass a map of arguments
        String secretTarget = (args.length > 0) ? args[0] : "AKIAIOSFODNN7EXAMPLE";
        Map<String, Object> arguments = Map.of("secret", secretTarget.trim());

        //Call the supervisor agent
        String secretType = (String) supervisorAgent.invoke(arguments);

        //Print results
        System.out.printf("==> Secret '%s' is of type '%s'.\n", secretTarget, secretType);
    }
}
