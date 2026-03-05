# POC n°4

## Goal

🎯 The goal of this POC is the allow me to discover:

* What is really an agent ?
* How to implement one ?
* What is the role of the LLM and which interaction are made?

## Topology

🤔 I decided to implement a **agent** that will validate the secret identified by the tool [gitleaks](https://github.com/gitleaks/gitleaks). The idea was taken from this other [POC](https://github.com/righettod/toolbox-codescan/tree/main/misc/poc01).

🤖 Input data was generated with **GEMINI** (model "Thinking"), **ChatGPT** (model "ChatGPT") and **Claude** (model "Sonnet 4.6 Extended") using the following user prompt:

```text
Create a extended sample json result file that is the output of this tool: https://github.com/gitleaks/gitleaks
```

🧑‍💻 Such data was not pushed as GH was refusing it even if fake data (that is normal).

## Technology stack

> 🤖 Model used is [qwen3-coder:480b-cloud](https://ollama.com/library/qwen3-coder).

* [langchain4j](https://docs.langchain4j.dev/) via its `langchain4j-agentic` module.
* Ollama via a [cloud version of a model](https://docs.ollama.com/cloud) to use a model intended to be used in a agent context: I used the "cloud" version of a model because my laptop was not able to handle a pure local version.

🤖 Model relay execution prior to run the POC:

```bash
$ ollama run qwen3-coder:480b-cloud
pulling manifest
pulling 476b4620b85b: 100%
verifying sha256 digest
writing manifest
success
Connecting to 'qwen3-coder:480b' on 'ollama.com' ⚡
>>> ...
```

## Pending test of attack vectors

🧑‍💻 Continue to explore the feature and properties of an agent...

## References & tools

### References

* <https://docs.langchain4j.dev/tutorials/agents>
