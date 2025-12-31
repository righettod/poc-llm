# POC-LLM

![Check POCs state](https://github.com/righettod/poc-llm/actions/workflows/check_state.yml/badge.svg?branch=main)

> [!NOTE]
> It is *start from scratch* from this [work](https://github.com/righettod/poc-llm/releases/tag/1.0.0).

📊 This repository represent my journey into AI (GenAI here as AI is a large domain) from an application security perspective.

📦 It contains the work I perform in order to explore the security aspects of an application using an LLM, this, in different integration topologies.

## Goal

🧑‍🎓 My goal is to identify and understand:

1. How such application is implemented from a technical perspective?
2. Which security weaknesses can occur when implementing such application?
3. How such weaknesses can be exploited and prevented?

## Topologies

> [!NOTE]
> Ollama is used to run the local LLM and java is used for the app technology.

📁 Each POC have its own folder.

Progress legends:

* 🧑‍💻 POC in progress.
* 🧑‍🎓 POC to be performed.
* ✅ POC finished and information centralized.

🎯 I want to explore the following topologies:

* ✅ [POC00](poc00/): App using a local LLM only.
* ✅ [POC01](poc01/): App using a local LLM with RAG.
* 🧑‍💻 [POC02](poc02/): App using a local LLM with Tools (Function Calling).
* 🧑‍🎓 POC03: An MCP server exposing several functions to a local LLM.
* 🧑‍🎓 POC04: App using LLM with a local MCP server.
* 🧑‍🎓 POC05: App that is an Agent using a local LLM.
* 🧑‍🎓 POC06: How secure is, from a source code perspective, an app developed using "Vibe Coding".

## Threat model

🐞 I try to centralize, into the mindmap below, the attack vectors I identified either:

* Directly via POCs.
* By reading referential/books.
* By asking help to GEMINI (model **2.5 Flash**) or ChatGPT (model **ChatGPT**).

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
    ⚒️ Tools
        Use a specific user prompt to call a tool with a malicious input parameter that will cause a malicious action on the system with which the tool with interact with. Can be to perform a create/update/delete operation or to read an unexpected information.
        Use a specific user prompt to call a tool with a malicious input parameter that will cause the tool to return a response that will contain a malicious content that will be returned to the app via the response of the LLM.
        Use a specific user prompt to ask the LLM to list the tool that it can call and then discover and use such hidden tools.
        Use a specific user prompt to assume an elevated role to induce the LLM to call a tool that the role of the current user is not allowed to call.
        Use a specific user prompt that manipulate the LLM reasoning so it selects a higher-risk tool even though another safer tool would be appropriate.
        Specific: When a tool, defined in the app, is configured to return its result directly and not send it back to the LLM then the tool can be used to access unexpected data or perform unexpected action in an easier way.
        Specific: Technical information disclosure due to an issue in the implementation of the handling of non existing tools, bad argument passed to a tool or any error occurring during the execution of a tool.
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

## Retex

🤝 The folder [feedback](feedback/) contains pending work on some blog posts to share my feedback regarding my exploration.

## Common resources and references used

### Book

* [Artificial Intelligence for Dummies](https://www.amazon.fr/dp/1394270712).
  * ✅ Read finished.
* [AI Engineering: Building Applications with Foundation Models](https://www.amazon.fr/dp/1098166302).
  * ✅ Read finished.
* [OWASP AI Testing Guide](https://owasp.org/www-project-ai-testing-guide/)
  * 📅 Printed and reading planned for begining of 2026.
* [The Agentic AI Bible](https://www.amazon.com/Agentic-Bible-Up-Date-Goal-Driven/dp/B0FL21R86Q).
  * 📅 Ordering and reading planned after the *OWASP AI Testing Guide*.  

### Training

* [SEC545: GenAI and LLM Application Security](https://www.sans.org/cyber-security-courses/genai-llm-application-security-5day).
  * 📅 Planned for 2026.

### OWASP

* <https://owasp.org/www-project-top-10-for-large-language-model-applications/>
* <https://genai.owasp.org/>

### Other

* <https://docs.langchain4j.dev/tutorials/tools/#returning-immediately-the-result-of-a-tool-execution-request>
* <https://docs.langchain4j.dev/tutorials/chat-and-language-models>
* <https://docs.langchain4j.dev/integrations/language-models/ollama>
* <https://docs.langchain4j.dev/category/tutorials>
* <https://docs.langchain4j.dev/tutorials/rag>
* <https://docs.langchain4j.dev/tutorials/tools>
* <https://blog.kulkan.com/assessing-the-attack-surface-of-remote-mcp-servers-92d630a0cab0>
* <https://blog.christianposta.com/the-updated-mcp-oauth-spec-is-a-mess/>
* <https://damienbod.com/2025/10/16/implement-a-secure-mcp-oauth-desktop-client-using-oauth-and-entra-id/>
