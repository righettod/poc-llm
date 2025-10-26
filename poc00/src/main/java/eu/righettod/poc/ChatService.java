package eu.righettod.poc;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

/**
 * Single controller exposing a service to chat with a LLM only (no tools or RAG).
 */
@RestController
public class ChatService {

    interface Assistant {
        @SystemMessage("You act as a instructor and you must provide the elements or figures to prove your reply. Remove every empty line from every response.")
        String chat(@MemoryId String memoryId, @UserMessage String message);
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

    @Value("${chat.memory.max.entries}")
    private int chatMemoryMaxEntries;

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
        this.chatAssistant = AiServices.builder(Assistant.class).chatModel(model).chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(this.chatMemoryMaxEntries)).build();
    }


    @PostMapping(value = "/ask", produces = MediaType.TEXT_PLAIN_VALUE, consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> ask(@RequestHeader(value = "X-Chat-Session-Id", required = false) String chatSessionId, @RequestBody String userMessage) {
        String sessionId = chatSessionId;
        if (chatSessionId == null) {
            sessionId = generateSessionId();
        }
        logger.info("[CALL] Call for session {}", sessionId);
        String llmResponse = chatAssistant.chat(sessionId, userMessage);
        return ResponseEntity.ok().header("X-Chat-Session-Id", sessionId).body(llmResponse);
    }

    private String generateSessionId() {
        String sessionId = UUID.randomUUID().toString();
        return Arrays.stream(sessionId.split("-")).toList().getLast();
    }

}
