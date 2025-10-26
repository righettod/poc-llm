package eu.righettod.poc;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single controller exposing a service to chat with a LLM only (no tools or RAG).
 */
@RestController
public class ChatService {

    interface Assistant {
        @SystemMessage("You act as a instructor and you must provide the elements or figures to prove your reply.")
        String chat(@UserMessage String message);
    }

    private final Logger logger = LoggerFactory.getLogger(ChatService.class);

    private final Map<String, Assistant> chatSessions = new ConcurrentHashMap<>();

    private OllamaChatModel model;

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
        this.model = OllamaChatModel.builder().baseUrl(this.ollamaBaseUrl)
                .modelName(this.ollamaModel)
                .timeout(Duration.ofSeconds(this.ollamaResponseTimeout))
                .temperature(this.ollamaModelCreativity)
                .logRequests(this.ollamaTraceExchange)
                .logResponses(this.ollamaTraceExchange)
                .responseFormat(ResponseFormat.TEXT)
                .build();
    }

    @GetMapping(value = "/start", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> startSession() {
        String sessionId = generateSessionId();
        logger.info("[INIT] Configure a new chat proxy for the session {}", sessionId);
        ChatMemory chatMemory = MessageWindowChatMemory.builder().id(sessionId).maxMessages(this.chatMemoryMaxEntries).build();
        Assistant chatAssistant = AiServices.builder(Assistant.class).chatModel(this.model).chatMemory(chatMemory).build();
        this.chatSessions.put(sessionId, chatAssistant);
        return ResponseEntity.ok(sessionId);
    }

    @PostMapping(value = "/ask", produces = MediaType.TEXT_PLAIN_VALUE, consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> ask(@RequestHeader("X-Chat-Session-Id") String chatSessionId, @RequestBody String userMessage) {
        Assistant chatAssistant = chatSessions.get(chatSessionId);
        if (chatAssistant == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Invalid session!");
        }
        logger.info("[CALL] Call for session {}", chatSessionId);
        String llmResponseJson = chatAssistant.chat(userMessage);
        return ResponseEntity.ok(llmResponseJson);
    }

    private String generateSessionId() {
        String sessionId = UUID.randomUUID().toString();
        return Arrays.stream(sessionId.split("-")).toList().getLast();
    }

}
