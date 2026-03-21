# POC n°4

## Goal

🎯 The goal of this POC is the allow me to discover:

* What is really an agent ?
* How to implement one ?
* What is the role of the LLM and which interaction are made?

## Topology

### GitLeaks sub poc

🤔 I decided to implement a **agent** that will validate the secret identified by the tool [gitleaks](https://github.com/gitleaks/gitleaks). The idea was taken from this other [POC](https://github.com/righettod/toolbox-codescan/tree/main/misc/poc01).

🤖 Input data was generated with **GEMINI** (model "Thinking"), **ChatGPT** (model "ChatGPT") and **Claude** (model "Sonnet 4.6 Extended") using the following user prompt:

```text
Create a extended sample json result file that is the output of this tool: https://github.com/gitleaks/gitleaks
```

ℹ️ Such data was not pushed as GH was refusing it even if it was fake data (that is normal).

🔬 Overview of the communication flows between agents:

```mermaid
sequenceDiagram
    actor U as User
    participant S as SupervisorAgent
    participant I as SecretIdentifierAgent
    participant T as Tools    
    participant M as Model
    U->>S: Submit a string data<br/>find by GitLeaks
    loop While the type of the data is not identified<br/>and iteration round count is inferior to 5
        S->>I: Ask to identify the type of data
        I->>M: Ask what is the data using<br/>its knowledge and provided<br/>specialized secret identification tools
        opt Ask to call a tools with the data
            M->>I: Provide the request to call one of the tools with the data
            I->>T: Call the tool with the data
            T->>I: The tool reply if YES or NO<br/>the data is of the corresponding type
            I->>M: Provide the response of the tool
        end        
        M->>I: Provide its response about the type of data or<br/> unknown if the data cannot be identified
        I->>S: Provide its response about the type of data or<br/> unknown if the data cannot be identified
        S->>S: Verify the exit condition based on the response and the current iteration round
        alt Exit condition is meet
            S->>S: End the loop
        else
            S->>S: Increment the iteration round<br/>and intiate a new round
        end
    end
    S->>U: Return the final response    
```

### SemGrep sub poc

TODO

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
