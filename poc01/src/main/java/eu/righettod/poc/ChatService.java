package eu.righettod.poc;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.FileSystems;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Single controller exposing a service to chat with a LLM only and using RAG.
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

    @Value("${ollama.trace.exchanges}")
    private boolean ollamaTraceExchange;


    @PostConstruct
    public void initializeModel() {
        logger.info("[ChatService][INIT] Configure the model execution...");
        OllamaChatModel model = OllamaChatModel.builder().baseUrl(this.ollamaBaseUrl)
                .modelName(this.ollamaModel)
                .timeout(Duration.ofSeconds(this.ollamaResponseTimeout))
                .temperature(this.ollamaModelCreativity)
                .logRequests(this.ollamaTraceExchange)
                .logResponses(this.ollamaTraceExchange)
                .responseFormat(ResponseFormat.TEXT)
                .build();
        logger.info("[ChatService][INIT] Load documents for RAG...");
        Instant start = Instant.now();
        List<Document> documents = FileSystemDocumentLoader.loadDocuments(Paths.get("documents"), FileSystems.getDefault().getPathMatcher("glob:*.pdf"));
        Instant finish = Instant.now();
        Duration timeElapsed = Duration.between(start, finish);
        System.out.printf("[ChatService] DEBUG - List of documents obtained in %s seconds.\n",timeElapsed.toSeconds());
        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
        start = Instant.now();
        EmbeddingStoreIngestor.ingest(documents, embeddingStore);
        finish = Instant.now();
        timeElapsed = Duration.between(start, finish);
        System.out.printf("[ChatService] DEBUG - List of documents loaded into InMemoryEmbeddingStore in %s seconds.\n",timeElapsed.toSeconds());
        logger.info("[ChatService][INIT] Create the chat assistant...");
        //MessageWindowChatMemory to 2 => 1 for the system prompt + 1 for the user prompt.
        this.chatAssistant = AiServices.builder(Assistant.class).chatModel(model).chatMemory(MessageWindowChatMemory.withMaxMessages(2)).contentRetriever(EmbeddingStoreContentRetriever.from(embeddingStore)).build();
    }


    @PostMapping(value = "/ask", produces = MediaType.TEXT_PLAIN_VALUE, consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> ask(@RequestBody String userMessage) {
        logger.info("[ChatService][CALL] Call");
        String llmResponse = chatAssistant.chat("default", userMessage);
        return ResponseEntity.ok().body(llmResponse);
    }


}
