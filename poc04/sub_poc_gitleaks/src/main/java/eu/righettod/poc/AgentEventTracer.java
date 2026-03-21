package eu.righettod.poc;

import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple listener to trace the different lifecyle event to understand the differents exchanges flow.
 * @see "https://docs.langchain4j.dev/tutorials/agents#observability"
 */
public class AgentEventTracer implements AgentListener {

    private final Logger logger = LoggerFactory.getLogger(AgentEventTracer.class);

    @Override
    public void afterAgenticScopeCreated(AgenticScope agenticScope) {
        logger.info("[EVENT:afterAgenticScopeCreated] Context '{}'.", agenticScope.agentInvocations().toString());
    }

    @Override
    public void afterAgentInvocation(AgentResponse agentResponse) {
        logger.info("[EVENT:afterAgentInvocation] Agent '{}' replied '{}'.", agentResponse.agentName(), agentResponse.output());
    }

    @Override
    public void afterToolExecution(ToolExecution toolExecution) {
        logger.info("[EVENT:afterToolExecution] Tool '{}' replied '{}'.", toolExecution.request().name(), toolExecution.result());
    }

    @Override
    public void beforeAgenticScopeDestroyed(AgenticScope agenticScope) {
        logger.info("[EVENT:beforeAgenticScopeDestroyed] Context '{}'.", agenticScope.agentInvocations().toString());
    }

    @Override
    public void beforeAgentInvocation(AgentRequest agentRequest) {
        logger.info("[EVENT:beforeAgentInvocation] Agent '{}' requested with '{}'.", agentRequest.agentName(), agentRequest.inputs().toString());
    }

    @Override
    public void beforeToolExecution(BeforeToolExecution beforeToolExecution) {
        logger.info("[EVENT:beforeToolExecution] Tool '{}' called with '{}'.", beforeToolExecution.request().name(), beforeToolExecution.request().arguments());
    }


    @Override
    public void onAgentInvocationError(AgentInvocationError agentInvocationError) {
        logger.info("[EVENT:onAgentInvocationError] Agent '{}' faced the error '{}'.", agentInvocationError.agentName(), agentInvocationError.error().getLocalizedMessage());
    }
}
