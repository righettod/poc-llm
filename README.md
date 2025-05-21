# Research on LLM

![Status](https://img.shields.io/badge/Status-Study_In_Progress-blue?style=flat)

![PackageLabsJar](https://github.com/righettod/poc-llm/actions/workflows/build.yml/badge.svg?branch=main)

## Objective

1. Study what is an LLM and how to use it from an application perspective.
2. Analyse the usage of LLM from an AppSec point of view (attacks and defenses).
3. Identify potential weaknesses on which attacks can be leveraged.

## Labs

🔬A labs has been created in order to study the different issues. This one take the context of a Chat model using RAG to get information about [Data Breach Investigations Report](https://www.verizon.com/business/resources/reports/dbir/) from the company [Verizon](https://www.verizon.com).

🧑‍💻The labs was developed using [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/download/) and is maven based.

📖Technology stack of the labs:
* [Ollama](https://ollama.com/): To have a local LLM engine.
* Ollama model [gemma3:1b](https://ollama.com/library/gemma3:1b): To have small model using only TEXT data.
* [LangChain4j](https://docs.langchain4j.dev/): To get the more nearest possible approach of the LLM concepts in the implementation of the labs.

📍Before to start the labs, start the model via `ollama run gemma3:1b` then, once the labs is started, you can call the model via the following HTTP request:

```bash
curl -H "Content-Type: text/plain" -d "What is the result of 1+1?" http://localhost:8080/ask
```
## Potential security weaknesses identified

### Malicious input

🐞If the input from the caller is used to build the **[SystemMessage](https://docs.langchain4j.dev/tutorials/ai-services#systemmessage)** then it can allow to affect the response given by the LLM. 

### Malicious output

🐞If a malicious content was present into the data used to train the LLM or to enrich it via RAG then it is possible that such content be returned by the LLM and, then, can be triggered depending on how the app uses the response of the LLM.

### Information disclosure

🐞If an LLM provided by an external provider is used, with RAG enriched with private documents, then private information will be shared with the LLM provider.

### Resource exhaustion

![RAQ QUERY](https://docs.langchain4j.dev/assets/images/rag-retrieval-f525d2937abc08fed5cec36a7f08a4c3.png)

🐞When RAG is used, during the **retrieval** phase, if the **[UserMessage](https://docs.langchain4j.dev/tutorials/ai-services/#usermessage)** is too vague then it can cause a huge query to be performed against the embedding store (**Query Embedding** step).

🐞In the same way, a huge *UserMessage* will be sent to the LLM that can cause extra cost due to the number of tokens present into the *UserMessage*.


## References used

* https://docs.langchain4j.dev/integrations/language-models/ollama
* https://docs.langchain4j.dev/integrations/language-models/ollama#parameters
* https://docs.langchain4j.dev/category/tutorials
* https://docs.langchain4j.dev/tutorials/model-parameters
* https://docs.langchain4j.dev/tutorials/rag
* https://docs.langchain4j.dev/tutorials/tools
* https://glaforge.dev/posts/2025/02/27/pretty-print-markdown-on-the-console/

