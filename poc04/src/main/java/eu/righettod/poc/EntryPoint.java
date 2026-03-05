package eu.righettod.poc;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;

/**
 * Entry point to run the POC.
 * @see "https://docs.langchain4j.dev/tutorials/agents#loop-workflow"
 */
public class EntryPoint {
    private static final String MODEL_NAME = "qwen3-coder:480b-cloud";
    private static final double MODEL_TEMPERATURE = 0.0;
    private static final String OLLAMA_BASE_URL = "http://localhost:11434/";
    private static final int OLLAMA_RESPONSE_TIMEOUT = 240;
    private static final int SUPERVISOR_AGENT_MAX_ITERATIONS = 5;
    private static final boolean DEBUG = true;

    private static final Logger LOGGER = LoggerFactory.getLogger(EntryPoint.class);

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
        SecretIdentifierTools secretIdentifierTools = new SecretIdentifierTools();
        SecretIdentifier secretIdentifierAgent = AgenticServices.agentBuilder(SecretIdentifier.class)
                .chatModel(model)
                .tools(secretIdentifierTools)
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
                    EntryPoint.LOGGER.info("[SUPERVISOR:exitCondition] Checking exit condition with secret type = '{}' => Exit ? {}.", secretType, mustExit);
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
