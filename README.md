# POC-LLM

![Check POCs state](https://github.com/righettod/poc-llm/actions/workflows/check_state.yml/badge.svg?branch=main)

> [!NOTE]
> It is *start from scratch* from this [work](https://github.com/righettod/poc-llm/releases/tag/1.0.0).

📊 This repository represent my journey into AI (GenAI here as AI is a large domain) from an application security perspective.

📦 It contains the work I perform in order to explore the security aspects:

- Of an application using an LLM, this, in different integration topologies.
- When leveraging the features of an coding assistant.

## Goal

🧑‍🎓 My goal is to identify and understand:

1. How such application is implemented from a technical perspective?
2. Which security weaknesses can occur when implementing such application?
3. How such weaknesses can be exploited and prevented?
4. How a coding assistant can be leveraged to include the security aspect?

## Topologies

> [!NOTE]
> Ollama is used to run the local LLM and java/python are used for the app technologies.

📁 Each POC have its own folder.

🗺️ Progress legends:

- 🧑‍🎓 POC **to be performed**.
- 🧑‍💻 POC **in progress**.
- ✅ POC **finished** and information centralized.

🎯 I want to explore the following topologies & concepts:

- ✅ [POC00](poc00/): App using a local LLM only.
- ✅ [POC01](poc01/): App using a local LLM with RAG.
- ✅ [POC02](poc02/): App using a local LLM with Tools (Function Calling).
- ✅ [POC03](poc03/): An MCP server exposing several functions to a local LLM.
- ✅ [POC04](poc04/): App that is an Agent using a local LLM.
- ✅ [POC05](poc05/): Exploration of Claude code command to perform [threat modeling](https://en.wikipedia.org/wiki/Threat_model) activity against a [User Story](https://www.atlassian.com/agile/project-management/user-stories).
- ✅ [POC06](poc06/): Explore the capability to leverage claude code to perform a secure code review of a code base.
- 🧑‍💻 [POC07](poc07/): Try to map for a SDLC every step in which GenAI can be used or bring useful help.

## Threat model

🐞 I try to centralize, into the mindmap below, the attack vectors I identified either:

- Directly via POCs.
- By reading referential/books.
- By asking help to GEMINI (models **2.5 Flash** / **Fast**) or ChatGPT (model **ChatGPT**).

```mermaid
mindmap
  root((Attack vectors))
    💻 Application
        Access or alter the system prompt.
        Execute or render malicious content returned by the LLM.
        Access to the internal identifier of a user prompt from the chat session the user prompt belongs to.
        Access to the internal identifier and the content of a user prompt from a chat session different than the chat session the user prompt belongs to.
    🤖 LLM
        Access to internal information of the LLM like for example training data.
        Ask to generate malicious output that will be triggered/rendered by the app.
        Crash the system process running the LLM or the machine itself due to usage of all resources.
    📚 RAG via the files store
        Insert a file with malicious content for which the content will be returned to the app via the LLM on a specific user prompt causing the content to be retrieved via RAG.
        Insert a file with malicious content that exploit a vulnerability present into a library used to parse the file when the embedding store is filled by an app.
        Sensitive information disclosure via RAG retrieval due to a file used to fill the embedding store and that was not expected to be because it contains sensitive or PII information.
        Insert one or several files with false content to cause the LLM to return false or inaccurate information.
        Legal or IP or licensing violations via inserted copyrighted content into the file store that will be returned to the app via the LLM through content retrieved via RAG.
        Access to the data of a document, for which the current user is not expected to have access to, because the app incorrectly or not check the authorization prior to load the corresponding document via RAG.
    ToolsGeneric["⚒️ Tools (general)"]
        Use a specific user prompt to call a tool with a malicious input parameter that will cause a malicious action on the system with which the tool will interact with. Can be used to perform a create/update/delete operation or to read an unexpected information.
        Use a specific user prompt to call a tool with a malicious input parameter that will cause the tool to return a response that will contain a malicious content that will be returned to the app via the response of the LLM.
        Use a specific user prompt to ask the LLM to list the tool that it can call and then discover and use such hidden tools.
        Use a specific user prompt to assume an elevated role to induce the LLM to call a tool that the role of the current user is not allowed to call.
        Use a specific user prompt that manipulate the LLM reasoning so it selects a higher-risk tool even though another safer tool would be appropriate.
        Specific: When a tool, defined in the app, is configured to return its result directly and not send it back to the LLM then the tool can be used to access unexpected data or perform unexpected action in an easier way.
        Specific: Technical information disclosure due to an issue in the implementation of the handling of non existing tools, bad argument passed to a tool or any error occurring during the execution of a tool.    
    ToolsMCP["🖥️ Tools (MCP server)"]
        Authentication issue affecting access to a tools/prompts/resources exposed.
        Authorization issue affecting access to a tools/prompts/resources exposed like for example trusting the role specified into a received parameter instead of the role defined in the access token.
        Authorization issue but internally: An MCP server requesting broader OAuth scopes than needed to perform a operation for a exposed tools.
        The implementation of the MCP server use a external library and the code of the library was compromised via a supply chain attacks so malicious code was inserted.
        The tools/prompts/resources are exposed using a insecure protocol like raw HTTP instead of HTTPS.
        The implementation for the tools/prompts/resources do not have protection to prevent resource exhaustion that could lead to a DOS.
        The implementation for the tools allow a caller to affect the metadata and descriptions returned by the MCP server for the affected tools.
        The implementation for the prompts allow a caller to affect the content of the prompts returned by the MCP server for the affected prompts in order to make the prompts return SYSTEM prompts instruction insead of USER prompts instructions.
        Common issues affecting web API.
    ⚙️ Agent
        Cause a DoS by falling in an infinite loop situation due the fact that the supervisor agent never received a reponse by a worker agent that fit the "exit" or "achieved" condition.
        Issues related to Tools usage if Tools are involved.
        Capability to act on the context instruction specified to the supervisor agent.
```

## Elements discovered during my study

### Junk returned by the model llama3.1 when using JSON format with LangChain4j

I was using `.responseFormat(ResponseFormat.JSON)` and I noticed that the model was returning me junk.

Example of call to the model through LangChain4j:

```txt
HTTP request:
- method: POST
- url: http://localhost:11434/api/chat
- headers: [Content-Type: application/json]
- body: {
  "model" : "llama3.1:latest",
  "messages" : [ {
    "role" : "system",
    "content" : "You act as a instructor and you must provide the elements or figures to prove your reply."
  }, {
    "role" : "user",
    "content" : "compute the result of 1 + 2."
  }, {
    "role" : "assistant",
    "content" : "{  }",
    "tool_calls" : [ ]
  }, {
    "role" : "user",
    "content" : "compute the result of 1 + 2."
  }, {
    "role" : "assistant",
    "content" : "{  \n\n\n\n\n\n  \n\n\n\n\n\n  \n\n\n\n\n\n  \n\n\n\n\n\n  \n\n\n\n\n\n  \n\n\n\n\n\n  \n\n\n\n\n\n  \n\n\n\n\n\n  \n\n\n\n\n\n  \n\n\n\n\n\n ",
    "tool_calls" : [ ]
  }, {
    "role" : "user",
    "content" : "compute the result of 1 + 2."
  } ],
  "options" : {
    "temperature" : 0.0,
    "stop" : [ ]
  },
  "format" : "json",
  "stream" : false,
  "tools" : [ ]
}
```

🤔 I restarted the ollama process, reloaded the model, checked my code without success. So, I asked to ChatGPT for insight about my problem and its reply was the following:

```text
This is a common issue when using Ollama + LangChain4j + format: "json" with models like Llama 3.1.

Llama 3.1 (and most llama3 family models) are not natively fine-tuned for JSON mode. 
Unlike OpenAI’s GPT-4 Turbo or Gemini models, Ollama’s models don’t automatically enforce
strict JSON syntax, so when you request format: "json", the model tries—but often fails—to comply.

That’s why you see junk.

It’s the model trying to start a JSON response ({) but it doesn’t know how to fill it
and Ollama truncates or filters invalid JSON output.
```

So, I moved back to `.responseFormat(ResponseFormat.TEXT)` to use TEXT format, it solved the problem and the model was correctly replying again 😊

## Feedback

🤝 The folder [feedback](feedback/) contains pending work on some blog posts to share my feedback regarding my exploration.

## Common resources and references used

### Book

- [Artificial Intelligence for Dummies](https://www.amazon.fr/dp/1394270712).
  - ✅ Read finished: Very interesting.
- [AI Engineering: Building Applications with Foundation Models](https://www.amazon.fr/dp/1098166302).
  - ✅ Read finished: Very interesting - A amazing book in terms of structure and content!
- [OWASP AI Testing Guide](https://owasp.org/www-project-ai-testing-guide/)
  - ✅ Read finished: Very interesting.
- [The Agentic AI Bible](https://www.amazon.com/Agentic-Bible-Up-Date-Goal-Driven/dp/B0FL21R86Q).
  - ⚠️ I rage quit the reading of the book and **made a return to Amazon!**
  - The book is an entire continuous wall of text that seems generated with AI. It don't provide effective and useful content.
- [Applied AI for Building Intelligent Systems](https://www.amazon.fr/dp/0135489687).  
  - 🔬 Read in progress.

### Training

- [SEC545: GenAI and LLM Application Security](https://www.sans.org/cyber-security-courses/genai-llm-application-security-5day).
  - ✅ Followed.

### OWASP

- <https://owasp.org/www-project-top-10-for-large-language-model-applications/>
- <https://genai.owasp.org/>

### Model Context Protocol Security

- <https://modelcontextprotocol-security.io/>
- <https://modelcontextprotocol.io/>
- <https://modelcontextprotocol-security.io/top10/server/>
- <https://modelcontextprotocol.io/docs/tutorials/security>
- <https://docs.spring.io/spring-ai/reference/2.0-SNAPSHOT/api/mcp/mcp-security.html>

### Tools

- <https://modelcontextprotocol.io/docs/tools/inspector>
- <https://github.com/modelcontextprotocol/inspector?tab=readme-ov-file#cli-mode>
- <https://github.com/f/mcptools>

### Other

- <https://docs.langchain4j.dev/tutorials/tools/#returning-immediately-the-result-of-a-tool-execution-request>
- <https://docs.langchain4j.dev/tutorials/chat-and-language-models>
- <https://docs.langchain4j.dev/integrations/language-models/ollama>
- <https://docs.langchain4j.dev/category/tutorials>
- <https://docs.langchain4j.dev/tutorials/rag>
- <https://docs.langchain4j.dev/tutorials/tools>
- <https://blog.kulkan.com/assessing-the-attack-surface-of-remote-mcp-servers-92d630a0cab0>
- <https://blog.christianposta.com/the-updated-mcp-oauth-spec-is-a-mess/>
- <https://damienbod.com/2025/10/16/implement-a-secure-mcp-oauth-desktop-client-using-oauth-and-entra-id/>
