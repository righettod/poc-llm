package eu.righettod.poc;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.FileSystems;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Single controller exposing a service to chat with a LLM only and using RAG.<br>
 * This controller use Metadata on Embeddings to apply authorization rules on retrieved data by RAG.
 */
@RestController
public class ChatServiceWithRAGMetadata {

    interface Assistant {
        @SystemMessage("You act as a instructor and you must provide the elements or figures to prove your reply. Remove every empty line from every response.")
        String chat(@MemoryId String memoryId, @UserMessage String message);
    }

    private static final String ROLE_METADATA_FIELD_NAME = "allowedAuthorizationRole";

    private final Logger logger = LoggerFactory.getLogger(ChatServiceWithRAGMetadata.class);

    private final Map<String, Assistant> chatAssistants = new HashMap<>();

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
        logger.info("[ChatServiceWithRAGMetadata][INIT] Configure the model execution...");
        OllamaChatModel model = OllamaChatModel.builder().baseUrl(this.ollamaBaseUrl)
                .modelName(this.ollamaModel)
                .timeout(Duration.ofSeconds(this.ollamaResponseTimeout))
                .temperature(this.ollamaModelCreativity)
                .logRequests(this.ollamaTraceExchange)
                .logResponses(this.ollamaTraceExchange)
                .responseFormat(ResponseFormat.TEXT)
                .build();
        logger.info("[ChatServiceWithRAGMetadata][INIT] Load documents for RAG...");
        Instant start = Instant.now();
        List<Document> documentsAllowedForAdminRole = FileSystemDocumentLoader.loadDocuments(Paths.get("separated-documents", "admin"), FileSystems.getDefault().getPathMatcher("glob:*.pdf"));
        List<Document> documentsAllowedForOtherRoles = FileSystemDocumentLoader.loadDocuments(Paths.get("separated-documents", "other"), FileSystems.getDefault().getPathMatcher("glob:*.pdf"));
        documentsAllowedForAdminRole.forEach(document -> {
            document.metadata().put(ROLE_METADATA_FIELD_NAME, "admin");
        });
        documentsAllowedForOtherRoles.forEach(document -> {
            document.metadata().put(ROLE_METADATA_FIELD_NAME, "other");
        });
        List<Document> allDocuments = new ArrayList<>();
        allDocuments.addAll(documentsAllowedForAdminRole);
        allDocuments.addAll(documentsAllowedForOtherRoles);
        Instant finish = Instant.now();
        Duration timeElapsed = Duration.between(start, finish);
        System.out.printf("[ChatServiceWithRAGMetadata] DEBUG - List of documents obtained in %s seconds.\n", timeElapsed.toSeconds());
        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
        start = Instant.now();
        EmbeddingStoreIngestor.ingest(allDocuments, embeddingStore);
        finish = Instant.now();
        timeElapsed = Duration.between(start, finish);
        System.out.printf("[ChatServiceWithRAGMetadata] DEBUG - List of documents loaded into InMemoryEmbeddingStore in %s seconds.\n", timeElapsed.toSeconds());
        logger.info("[ChatServiceWithRAGMetadata][INIT] Create the map of chat assistants...");
        //MessageWindowChatMemory to 2 => 1 for the system prompt + 1 for the user prompt.
        //Create the chat assistant with a content retriever matching documents intented for the authorization role "admin"
        //So it includes all documents of the store
        Filter metadataFilterForAdminRole = Filter.or(MetadataFilterBuilder.metadataKey(ROLE_METADATA_FIELD_NAME).isEqualTo("admin"),MetadataFilterBuilder.metadataKey(ROLE_METADATA_FIELD_NAME).isEqualTo("other"));
        ContentRetriever contentRetrieverForAdminRole = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .filter(metadataFilterForAdminRole)
                .build();
        Assistant chatAssistantForAdminRole = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(2))
                .contentRetriever(contentRetrieverForAdminRole)
                .build();
        this.chatAssistants.put("admin", chatAssistantForAdminRole);
        //Create the chat assistant with a content retriever matching documents intented for any authorization role other than "admin"
        //So it includes all documents of the store excepting ones intented for the admin role only
        Filter metadataFilterForOtherRoles = MetadataFilterBuilder.metadataKey(ROLE_METADATA_FIELD_NAME).isNotEqualTo("admin");
        ContentRetriever contentRetrieverForOtherRoles = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .filter(metadataFilterForOtherRoles)
                .build();
        Assistant chatAssistantForOtherRoles = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(2))
                .contentRetriever(contentRetrieverForOtherRoles)
                .build();
        this.chatAssistants.put("other", chatAssistantForOtherRoles);
    }


    @PostMapping(value = "/askWithUserRole/{roleName}", produces = MediaType.TEXT_PLAIN_VALUE, consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> ask(@RequestBody String userMessage, @PathVariable("roleName") String roleName) {
        logger.info("[ChatServiceWithRAGMetadata][CALL] Call using role name '{}'", roleName);
        Assistant chatAssistant = this.chatAssistants.getOrDefault(roleName, this.chatAssistants.get("other"));
        String llmResponse = chatAssistant.chat("default", userMessage);
        return ResponseEntity.ok().body(llmResponse);
    }


}
