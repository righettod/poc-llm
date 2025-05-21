# Research on LLM

![Status](https://img.shields.io/badge/Status-Study_In_Progress-blue?style=for-the-badge)

## Objective

1. Study what is an LLM and how to use it from an application perspective.
2. Analyse the usage of LLM from an AppSec point of view (attacks and defenses).
3. Identify potential weaknesses on which attacks can be leveraged.

## Labs

🔬A labs has been created in order to study the different issues. This one take the context of a Chat model using RAG to get information about [Data Breach Investigations Report](https://www.verizon.com/business/resources/reports/dbir/) edition 2025 from the company [Verizon](https://www.verizon.com).

🧑‍💻The labs was developed using [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/download/) and is maven based.

📖Technology stack of the labs:
* [Ollama](https://ollama.com/): To have a local LLM engine.
* Ollama model [gemma3:1b](https://ollama.com/library/gemma3:1b): To have small model using only TEXT data.
* [LangChain4j](https://docs.langchain4j.dev/): To get the more nearest possible of the LLM concepts in the implementation of the labs.

📍Before to start the labs, start the model via `ollama run gemma3:1b` then, once the labs is started, you can call the model via the following HTTP request:

```bash
curl -H "Content-Type: text/plain" -d "What is the result of 1+1?" http://localhost:8080/ask
```

## References used

* https://docs.langchain4j.dev/category/tutorials

