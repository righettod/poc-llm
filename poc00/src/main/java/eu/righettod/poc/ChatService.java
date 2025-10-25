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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Single controller exposing a service to chat with a LLM only (no tools or RAG).
 */
@RestController
public class ChatService {

    interface Assistant {
        @SystemMessage("You act as a instructor and you must provide the elements or figures to prove your reply.")
        String chat(@UserMessage String message);
    }

    private Assistant chatAssistant;

    private final Logger logger = LoggerFactory.getLogger(ChatService.class);

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
        OllamaChatModel model = OllamaChatModel.builder().baseUrl(ollamaBaseUrl)
                .modelName(ollamaModel)
                .timeout(Duration.ofSeconds(ollamaResponseTimeout))
                .temperature(ollamaModelCreativity)
                .logRequests(ollamaTraceExchange)
                .logResponses(ollamaTraceExchange)
                .responseFormat(ResponseFormat.JSON)
                .build();
        logger.info("[INIT] Configure the chat proxy...");
        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(chatMemoryMaxEntries);
        chatAssistant = AiServices.builder(Assistant.class).chatModel(model).chatMemory(chatMemory).build();
    }

    @PostMapping(value = "/ask", produces = MediaType.TEXT_HTML_VALUE, consumes = MediaType.TEXT_PLAIN_VALUE)
    public String ask(@RequestBody String userMessage) {
        return chatAssistant.chat(userMessage);
    }

    /**
     * Pretty-print Markdown on the console.<br>
     * All credits goes to Guillaume Laforge (see the referenced link).
     *
     * @param md Markdown content.
     * @return Content converted to console output.
     * @see "https://glaforge.dev/posts/2025/02/27/pretty-print-markdown-on-the-console/"
     */
    private String markdownToConsole(String md) {
        return md
                // Bold
                .replaceAll("\\*\\*(.*?)\\*\\*", "\u001B[1m$1\u001B[0m")
                // Italic
                .replaceAll("\\*(.*?)\\*", "\u001B[3m$1\u001B[0m")
                // Underline
                .replaceAll("__(.*?)__", "\u001B[4m$1\u001B[0m")
                // Strikethrough
                .replaceAll("~~(.*?)~~", "\u001B[9m$1\u001B[0m")
                // Blockquote
                .replaceAll("(> ?.*)",
                        "\u001B[3m\u001B[34m\u001B[1m$1\u001B[22m\u001B[0m")
                // Lists (bold magenta number and bullet)
                .replaceAll("([\\d]+\\.|-|\\*) (.*)",
                        "\u001B[35m\u001B[1m$1\u001B[22m\u001B[0m $2")
                // Block code (black on gray)
                .replaceAll("(?s)```(\\w+)?\\n(.*?)\\n```",
                        "\u001B[3m\u001B[1m$1\u001B[22m\u001B[0m\n\u001B[57;107m$2\u001B[0m\n")
                // Inline code (black on gray)
                .replaceAll("`(.*?)`", "\u001B[57;107m$1\u001B[0m")
                // Headers (cyan bold)
                .replaceAll("(#{1,6}) (.*?)\n",
                        "\u001B[36m\u001B[1m$1 $2\u001B[22m\u001B[0m\n")
                // Headers with a single line of text followed by 2 or more equal signs
                .replaceAll("(.*?\n={2,}\n)",
                        "\u001B[36m\u001B[1m$1\u001B[22m\u001B[0m\n")
                // Headers with a single line of text followed by 2 or more dashes
                .replaceAll("(.*?\n-{2,}\n)",
                        "\u001B[36m\u001B[1m$1\u001B[22m\u001B[0m\n")
                // Images (blue underlined)
                .replaceAll("!\\[(.*?)]\\((.*?)\\)",
                        "\u001B[34m$1\u001B[0m (\u001B[34m\u001B[4m$2\u001B[0m)")
                // Links (blue underlined)
                .replaceAll("!?\\[(.*?)]\\((.*?)\\)",
                        "\u001B[34m$1\u001B[0m (\u001B[34m\u001B[4m$2\u001B[0m)");
    }

}
