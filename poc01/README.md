# POC n°0

## Topology

App using a local LLM with RAG.

## Technology stack

* [langchain4j](https://github.com/langchain4j/langchain4j) for the integration with a local LLM and have access to low level exchange with the LLM.
  * Every time it is possible I stick to the way proposed in [samples](https://github.com/langchain4j/langchain4j-examples/tree/main) in order to use the framework.
* [springboot](https://spring.io/projects/spring-boot) for exposing simple services.

## Run the POC

💻 Step 1 - Execute in a shell window:

```powershell
# ollama pull llama3.1:latest
PS> ollama run llama3.1:latest
```

💻 Step 2 - Execute the run configuration **StartApp** from Intellij IDEA.

💻 Now you can use the script [client.ps1](client.ps1) to exchange with the model.

## Pending test of attack vectors

TODO

## Notes about attack vectors

### 😈 Insert a file with malicious content for which the content will be returned to the app via the LLM on a specific user prompt causing the content to be retrieved via RAG

"Malicious" document inserted into the documents store and user prompt used to retrieve its content via the app + LLM:

![prt00](images/prt00.png)
