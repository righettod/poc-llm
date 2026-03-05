package eu.righettod.poc;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Define the agent in charge of identify which type of secret a provided string is.
 *
 * @see "https://docs.langchain4j.dev/tutorials/agents"
 * @see "https://github.com/langchain4j/langchain4j-examples/blob/main/agentic-tutorial/src/main/java/_3_loop_workflow/_3a_Loop_Agent_Example.java"
 */
public interface SecretIdentifier {

    @Agent("Analyse a secret to identify which type of secret it is")
    @SystemMessage("""
            You are a secret classification assistant.
            
            You must use the available tools to identify secret types.
            
            Instructions:
            - Analyze the input string.
            - If the string matches the format handled by a tool, call that tool.
            - Do not classify secrets manually if a relevant tool exists.
            
            Never provide explanations.
            Return only the classification when not calling a tool.
            """)
    @UserMessage("Analyze this secret: {{secret}}")
    String identitySecretType(@MemoryId String memoryId, @V("secret") String secret);
}
