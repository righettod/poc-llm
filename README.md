# Research on LLM

![Status](https://img.shields.io/badge/Status-Study_In_Progress-blue?style=flat)

![PackageLabsJar](https://github.com/righettod/poc-llm/actions/workflows/build.yml/badge.svg?branch=main)

## Objective

1. Study what is an LLM and how to use it from an application perspective.
2. Analyse the usage of LLM from an AppSec point of view (attacks and defenses).
3. Identify potential weaknesses on which attacks can be leveraged.

## Labs

> [!CAUTION]
> **gemma3:1b** model does not support tools so I needed to use **llama3.1:latest** model instead.

🔬 A labs has been created in order to study the different issues. This one take the context of a Chat model using RAG to get information about [Data Breach Investigations Report](https://www.verizon.com/business/resources/reports/dbir/) from the company [Verizon](https://www.verizon.com).

🧑‍💻 The labs was developed using [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/download/) and is maven based.

📖 Technology stack of the labs:
* [Ollama](https://ollama.com/)
  * To have a local LLM engine.
* Ollama model [llama3.1:latest](https://ollama.com/library/llama3.1)
  * To have *small model* using only TEXT data.
* [LangChain4j](https://docs.langchain4j.dev/)
  * To get the more nearest possible approach of the LLM concepts in the implementation of the labs that is a *application leveraging a LLM*.
* [SpringAI](https://docs.spring.io/spring-ai/reference/)
  * To implement a *Model Context Protocol (MCP) server*, for which, exposed tools will be consumed by the app (application leveraging a LLM) via the MCP client provided by *langchain4j*.
  * Used because *langchain4j* was not supporting the creation of a MCP server at the time when this POC was created.

## Run the labs

💻 Step 1 - Execute in a shell window:

```powershell
PS> ollama pull llama3.1:latest
PS> ollama run llama3.1:latest
```

💻 Step 2 - Execute the run configuration **StartMCPServer** from **Intellij IDEA**.

💻 Step 3 - Execute the run configuration **StartLLMBasedApplication** from **Intellij IDEA**.

💻 Now you can call the model via the following HTTP request in another shell window or use the script [client.ps1](client.ps1):

```powershell
PS> curl -H "Content-Type: text/plain" -d "What is the result of 1+1?" http://localhost:8080/ask
```

## Communication flow between the app and the LLM

💡 See [here](flow.md) for details.

## Tools (Function Calling) vs Model Context Protocol (MCP) Server

💡 See [here](tools-vs-mcp.md) for details.

## Potential security weaknesses identified on a application leveraging a LLM

### Malicious input

🐞 If the input from the caller is used to build the **[SystemMessage](https://docs.langchain4j.dev/tutorials/ai-services#systemmessage)** then it can allow to affect the response given by the LLM. 

🐞 When custom functions are used, a caller can use instructions into its *UserMessage* to call functions with a malicious parameter to abuse the function processing for different kinds of injections (SQLI, XSS, etc).

### Malicious output

🐞 If a malicious content was present into the data used to train the LLM or to enrich it via RAG then it is possible that such content be returned by the LLM and, then, can be triggered depending on how the app uses the response of the LLM.

🐞 When custom functions are used, a caller can use instructions into its *UserMessage* to call functions with a malicious parameter to abuse the function processing to retrieve malicious content from a location controlled by the attacker. Such malicious content can be triggered depending on how the app uses the response of the LLM.

### Information disclosure

🐞 If an LLM provided by an external provider is used, with RAG enriched with private documents, then private information will be shared with the LLM provider.

🐞 When custom functions are used, a caller can use instructions into its *UserMessage* to call functions in order to discover potential hidden functions based on a [discrepancy factor](https://cwe.mitre.org/data/definitions/204.html) in the response.

### Resource exhaustion

![RAQ QUERY](https://docs.langchain4j.dev/assets/images/rag-retrieval-f525d2937abc08fed5cec36a7f08a4c3.png)

🐞 When RAG is used, during the **retrieval** phase, if the **[UserMessage](https://docs.langchain4j.dev/tutorials/ai-services/#usermessage)** is too vague then it can cause a huge query to be performed against the embedding store (**Query Embedding** step).

🐞 In the same way, a huge *UserMessage* will be sent to the LLM that can cause extra cost due to the number of tokens present into the *UserMessage*.

🐞 When custom functions are used, a caller can use instructions into its *UserMessage* to call functions in order to cause the LLM to request many custom functions call to the app handling the call and then overflow it.

### Authorization issue

🐞 When custom functions are used, a caller can use instructions into its *UserMessage* to call functions that it is not expected to be able to call.

🐞 Depending on how chat session are isolated between users, it could be possible from the user A to hijack the chat session of the user B and then retrieve its chat history. 

## References used

### Websites

#### Langchain4J

* https://docs.langchain4j.dev/integrations/language-models/ollama
* https://docs.langchain4j.dev/integrations/language-models/ollama#parameters
* https://docs.langchain4j.dev/category/tutorials
* https://docs.langchain4j.dev/tutorials/model-parameters
* https://docs.langchain4j.dev/tutorials/rag
* https://docs.langchain4j.dev/tutorials/tools
* https://docs.langchain4j.dev/tutorials/tools/#tools-hallucination-strategy
* https://docs.langchain4j.dev/tutorials/mcp

#### SpringAI

* https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html

#### Misc

* https://glaforge.dev/posts/2025/02/27/pretty-print-markdown-on-the-console/

### Books

* [Artificial Intelligence for Dummies](https://www.amazon.fr/dp/1394270712).
* [AI Engineering: Building Applications with Foundation Models](https://www.amazon.fr/dp/1098166302).

