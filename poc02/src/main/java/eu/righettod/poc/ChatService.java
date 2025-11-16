package eu.righettod.poc;

import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.*;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Single controller exposing a service to chat with a LLM only and using Tools (Function Calling).
 */
@RestController
public class ChatService {

    interface Assistant {
        @SystemMessage("You act as a instructor and you must provide the elements or figures to prove your reply. Remove every empty line from every response.")
        Result<String> chat(@MemoryId String memoryId, @UserMessage String message);
    }

    private final Logger logger = LoggerFactory.getLogger(ChatService.class);

    private Assistant chatAssistant;

    @Value("${ollama.baseurl}")
    private String ollamaBaseUrl;

    @Value("${ollama.model.name}")
    private String ollamaModel;

    @Value("${ollama.model.temperature}")
    private double ollamaModelCreativity;

    @Value("${ollama.model.response.timeout}")
    private int ollamaResponseTimeout;

    @Value("${ollama.trace.exchanges}")
    private boolean ollamaTraceExchange;


    @PostConstruct
    public void initializeModel() {
        logger.info("[INIT] Configure the model execution...");
        OllamaChatModel model = OllamaChatModel.builder().baseUrl(this.ollamaBaseUrl)
                .modelName(this.ollamaModel)
                .timeout(Duration.ofSeconds(this.ollamaResponseTimeout))
                .temperature(this.ollamaModelCreativity)
                .logRequests(this.ollamaTraceExchange)
                .logResponses(this.ollamaTraceExchange)
                .responseFormat(ResponseFormat.TEXT)
                .build();
        //MessageWindowChatMemory to 2 => 1 for the system prompt + 1 for the user prompt.
        final CustomTools tools = new CustomTools();
        this.chatAssistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .tools(tools)
                .hallucinatedToolNameStrategy(
                        toolExecutionRequest -> ToolExecutionResultMessage.from(
                                toolExecutionRequest,
                                String.format("Error: there is no tool called %s (%s).", toolExecutionRequest.name(), tools.getToolsList())
                        )
                )
                .toolArgumentsErrorHandler(
                        (error, errorContext) -> {
                            String callContext = String.format("Invocation Params: %s", errorContext.invocationParameters().toString());
                            String errorMsg = String.format("Error: %s", error.getMessage());
                            String msg = String.format("Something is wrong with tool arguments.\n%s\n%s\n", callContext, errorMsg);
                            return ToolErrorHandlerResult.text(msg);
                        }
                )
                .toolExecutionErrorHandler(
                        (error, errorContext) -> {
                            String errorMsg = String.format("Error: %s", error.getMessage());
                            String msg = String.format("Something is wrong with tool execution.\n%s\n", errorMsg);
                            return ToolErrorHandlerResult.text(msg);
                        }
                )
                .build();
    }


    @PostMapping(value = "/ask", produces = MediaType.TEXT_PLAIN_VALUE, consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> ask(@RequestBody String userMessage) {
        logger.info("[CALL] Call");
        Result<String> llmResponse = chatAssistant.chat("default", userMessage);
        String data = llmResponse.content();
        //See https://docs.langchain4j.dev/tutorials/tools/#returning-immediately-the-result-of-a-tool-execution-request
        //When "@Tool(returnBehavior = ReturnBehavior.IMMEDIATE)" is used for a tools and this one is invoked then
        //Result will have a "null content" (Result.content() is null) then data will have to be retrieved from the "Result.toolExecutions()"
        if (data == null) {
            StringBuilder buffer = new StringBuilder();
            for (ToolExecution toolExecution : llmResponse.toolExecutions()) {
                //Quick and dirty as I'm in a POC :)
                buffer.append(toolExecution.result()).append(" ");
            }
            data = buffer.toString();
        }
        return ResponseEntity.ok().body(data);
    }


}
