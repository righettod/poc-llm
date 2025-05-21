package eu.righettod.poc;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Single controller exposing a service to chat with the LLM.
 */
@RestController
public class Controller {

    interface Assistant {
        @SystemMessage("You act as a instructor and you give shortest response possible.")
        String chat(@UserMessage String message);
    }

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

    @Value("${debug}")
    private boolean debug;

    @PostConstruct
    public void initializeModel() {
        //Configure the model execution
        OllamaChatModel model = OllamaChatModel.builder().baseUrl(ollamaBaseUrl)
                .modelName(ollamaModel)
                .timeout(Duration.ofSeconds(ollamaResponseTimeout))
                .temperature(ollamaModelCreativity)
                .logRequests(debug)
                .logResponses(debug)
                .build();

        //Configure the "memory" of the chat to keep context across several questions
        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(chatMemoryMaxEntries);

        //Configure the chat proxy
        chatAssistant = AiServices.builder(Assistant.class).chatModel(model).chatMemory(chatMemory).build();
    }

    @PostMapping(value = "/ask", produces = MediaType.TEXT_PLAIN_VALUE, consumes = MediaType.TEXT_PLAIN_VALUE)
    public String ask(@RequestBody String userMessage) {
        return chatAssistant.chat(userMessage);
    }
}
