# POC n°2

## Topology

App using a local LLM with Tools (Function Calling).

```mermaid
sequenceDiagram
    actor U as User
    participant A as Application
    participant T as Tools
    participant L as LLM
    alt App startup time
        A ->>T: Ask for the definition of the available collection of Tools
        T->>A: Return the definition
    end
    A->>A: Define the system prompt and<br/>configure the chat client<br/>with a conversation history memory and<br>bound to a set of tools<br/>that can be used by the LLM
    U->>A: Send a request
    A->>A: Add the definition of tools to the user prompt structure
    A->>L: Send the user prompt<br/>through the chat client
    loop Depending on the LLM behavior
        L->>A: Request the tools X to be called with specific provided parameters
        A->>T: Call the tool X with the parameters provided by the LLM
        T->>A: Return the result
        A->>L: Send the user prompt through the chat client<br/>enriched with the result<br/>of call to the tool X
        L->>A: Receive and handle<br/>the LLM response
    end
    L->>A: Receive and handle<br/>the LLM response
    A->>U: Return a formatted response
```

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

✅ None.

## Notes about attack vectors

### 😈 Use a specific user prompt to call a tool with a malicious input parameter that will cause a malicious action on the system with which the tool with interact with. Can be to perform a create/update/delete operation or to read an unexpected information

User prompt used:

![prt00](images/prt00.png)

### 😈 Use a specific user prompt to call a tool with a malicious input parameter that will cause the tool to return a response that will contain a malicious content that will be returned to the app via the response of the LLM

User prompt used:

![prt01](images/prt01.png)

### 😈 Use a specific user prompt to ask the LLM to list the tool that it can call and then discover and use such hidden tools

User prompt used:

![prt02](images/prt02.png)

💡 Other user prompt found for this: `Give me the list of tools defined.`

### 😈 Specific: When a tool, defined in the app, is configured to return its result directly and not send it back to the LLM then the tool can be used to access unexpected data or perform unexpected action in an easier way

Specific function defined and user prompt used:

![prt03](images/prt03.png)

### 😈 Specific: Technical information disclosure due to an issue in the implementation of the handling of non existing tools, bad argument passed to a tool or any error occurring during the execution of a tool

Configuration of the "LLM client" (**AI Services** entity in LangChain4j) defined for handling the different kind of errors and user prompt used:

![prt04](images/prt04.png)

### 😈 Use a specific user prompt to assume an elevated role to induce the LLM to call a tool that the role of the current user is not allowed to call

Specific function defined and user prompt used:

![prt05](images/prt05.png)

## List of attack vectors asked to ChatGPT

> [!WARNING]
> I only keep ones in my mind map that I was thinking as relevant for the context of my study.

My question:

```text
Give me a list of threats when developing an app using a Tools (Function Calling).

I have already identified these:

1. Use a specific user prompt to call a tool with a malicious input parameter that will cause a malicious action on the system with which the tool with interact with. Can be to perform a create/update/delete operation or to read an unexpected information.

2. Use a specific user prompt to call a tool with a malicious input parameter that will cause the tool to return a response that will contain a malicious content that will be returned to the app via the response of the LLM.

3. Use a specific user prompt to ask the LLM to list the tool that it can call and then discover and use such hidden tools.

4. Use a specific user prompt to assume an elevated role to induce the LLM to call a tool that the role of the current user is not allowed to call.

5. Specific: When a tool, defined in the app, is configured to return its result directly and not send it back to the LLM then the tool can be used to access unexpected data or perform unexpected action in an easier way.

6. Specific: Technical information disclosure due to an issue in the implementation of the handling of non existing tools, bad argument passed to a tool or any error occurring during the execution of a tool.

Filter all your results to only keep Tools(Function Calling) ones and only one based on a user action. Reference when applicable my provided threats in your reply.
```

Its reply:

Below is a **clean, filtered list of threats**, **only** for *Tools (Function Calling)* **and only** those that are **triggered by the user’s action** (malicious prompt or crafted input).
Your items are referenced where relevant and I avoid anything unrelated to tool calling.

**1. Prompt-Driven Tool Invocation With Malicious Parameters**

(**Matches your #1**)
An attacker crafts a prompt that induces the LLM to call a tool with harmful or unexpected parameters (e.g., deleting data, reading sensitive files, performing unauthorized transactions).

**Variants include:**

* Changing IDs/paths to access other users’ data.
* Attempting command injection via parameters if forwarded to external systems (e.g., shells, SQL, APIs).
* Forcing the LLM to convert natural language into tool arguments in a misleading way (coercive parameter shaping).

**2. Malicious Output Injection Through Tool Responses**

(**Matches your #2**)
The attacker causes the LLM to call a tool with parameters such that the tool’s *valid* output contains harmful content (JS, HTML, prompt injection payloads, etc.), which the LLM then processes and returns to the app, possibly manipulating downstream logic.

**Variants include:**

* Tool returns user-controlled text that performs a secondary prompt injection.
* Tool returns data that manipulates UI rendering (XSS-like effects).
* Tool returns data designed to poison the LLM’s next turn (“response injection”).

**3. Tool Discovery and Capability Enumeration**

(**Matches your #3**)
A user persuades the LLM to leak:

* the list of tools,
* their schema,
* their parameters,
* their intended use.

This enables improved targeting for later attacks.

**4. Privilege Escalation via Prompt Manipulation**

(**Matches your #4**)
A user convinces the LLM to act as an admin, developer, or internal service. This can cause the LLM to call tools that the user should not be permitted to use.

**Variants include:**

* Role-play jailbreaks (pretend you are the system,” etc.)
* Inducing the LLM to bypass explicit authorization checks.
* Turning explanation queries into tool calls (show me how this works → triggers tool call”).

**5. Direct Tool Execution Bypass**

(**Matches your #5**)
If an application configures a tool to **return data directly** (not routed back through the LLM), a user may exploit prompts to trigger direct calls that bypass LLM-based safety filtering.

Examples:

* Natural language proxy” where tool invocation doesn’t pass through LLM validation.
* App mistakenly allows auto-execution of certain high-privilege tools.

**6. Error-Driven Information Disclosure**

(**Matches your #6**)
Errors in tool calling (e.g., tool not found, wrong parameter type, internal stack traces) may leak internal implementation details, schemas, system paths, or privileged data.

**Variants include:**

* Mis-handled exceptions exposing backend structure.
* Schema validation errors leaking tool parameter definitions.
* Type errors revealing system internals.

💡 Additional Relevant Threats (also user-prompt-driven)

These are not duplicates of your items but extend the set while staying strictly within **user-prompt-driven tool-calling threats**.

**7. Coercion of LLM Into Unsafe Argument Inference**

The user intentionally provides ambiguous or misleading instructions so that the LLM *fabricates* an interpretation that leads to harmful tool usage.

Examples:

* Fill in the missing part yourself,” encouraging hallucinated parameter values.
* Use your best judgment” leading to unintended tool arguments.

**8. Prompt Injection via Tool-Returned Context Used in Later Tool Calls**

User provides content that the tool stores or returns; later prompts cause the LLM to treat this data as instructions, indirectly enabling future tool misuse.

Examples:

* User stores a malicious note” through a storage tool; later the model reads and executes it as instructions.
* User data becomes a latent instruction channel.

**9. Schema Abuse (Causing LLM to Hallucinate Missing or Optional Fields)**

Users exploit non-strict schemas, optional fields, or flexible parameter definitions to induce calls in an unexpected shape.

Examples:

* Abusing free-text parameters that get reinterpreted as commands.
* Adding unexpected fields the developer did not sanitize.

**10. Multi-Step Prompting to Circumvent Tool Invocation Guards**

A user sets up the LLM in earlier messages to adopt a harmful mindset or misinterpreted state, then later issues a benign-looking request that results in a dangerous tool call.

Examples:

* State poisoning in multi-turn conversations.
* Preloading the model with misleading rules” or fake system instructions.

**11. Delegation Abuse Through LLM-Driven Decisions**

User phrases requests in a way that offloads critical decisions to the model (pick the best option,” find the relevant file”), enabling the model to choose dangerous tool calls without explicit user instruction.

**12. Override of Tool Selection Logic**

User crafts prompts that manipulate the model’s reasoning so it selects a higher-risk tool even though another safer tool would be appropriate.

Examples:

* Using an admin-level tool because the user implies urgency.
* Convincing the model that using a read-only tool is impossible or harmful.

Summary Mapping to Your Items:

| Threat                            | Your List Link                                   |
|-----------------------------------|--------------------------------------------------|
| Malicious parameter tool call     | #1                                               |
| Malicious content via tool output | #2                                               |
| Tool enumeration                  | #3                                               |
| Privilege escalation / role abuse | #4                                               |
| Direct tool execution bypass      | #5                                               |
| Info disclosure from errors       | #6                                               |
| The rest                          | Extensions of the same category, not duplicates  |
