"""
This POC define a workflow to classify a finding found by SemGrep as a "False Positive" or "True Positive"
using technical information about the class of vulnerability identified combined with a model able to read code.

This version of the POC use Claude Code to perform the code evaluation.

Dependencies:
    pip install -U langgraph claude-agent-sdk httpx ipython rich
"""

import asyncio
import json
import sys
from pathlib import Path
from typing import Any, cast

import httpx
from claude_agent_sdk import ClaudeAgentOptions, query
from langgraph.graph import END, START, StateGraph
from typing_extensions import TypedDict

# ---------------------------------------
# Constants
# ---------------------------------------
AGENT_MERMAID_IMAGE_FILE = "workflow-overview-with-claude-code.png"
SEMGREP_TEST_REPORT_FILE = "semgrep-data-python.json"
VULNERABLE_CODEBASE_HOME = "vulnerable-codebase"
DEFAULT_ENCODING = "utf-8"
SYSTEM_PROMPT_CLASSIFIER = """
You are an assistant specializing in source code analysis focusing on security vulnerabilities. Your primary objective is to determine if a given security vulnerability is truly present and exploitable within a provided source code.

Given a source code and a description of a security vulnerability, output a reply indicating if the given security vulnerability is really present or not. You must operate solely on the code provided and not make assumptions about potential exposure to code changes, external security controls, or other components.

**Strict Rules and Assumptions:**

* **No External Assumptions:** You must not make any assumptions about external security controls such as Web Application Firewalls (WAFs), reverse proxies, or external data sanitizers.
* **No Encoding Assumptions:** You must not assume a specific data encoding (e.g., UTF-8, ASCII) unless the source code explicitly specifies it.
* **No Privilege Assumptions:** You must assume a "worst-case scenario" security model, where a malicious actor has full control over the input.
* **Code is the Single Source of Truth:** The only valid security controls are those you can explicitly identify by tracing the data flow within the provided source code. If a control is not present in the code, it does not exist for the purpose of this analysis.

**Strict Evaluation of Code Logic:**

* **Fail-Fast on Rejection:** If any sanitization, validation, or transformation step (like a regular expression or a `replace` function) completely rejects the input or renders the proposed exploit ineffective, you must immediately conclude that the vulnerability is not present. The analysis ends here.
* **Regex is Absolute:** When a regular expression is used for validation, treat it as an absolute and unbreakable control. If the input does not match the regex, the payload is invalid, the vulnerable line is not reached, and the vulnerability is not present. No further payload analysis is required.
* **Payloads Only if Not Rejected:** Only attempt to formulate and test an exploit payload if no sanitization/validation step has already blocked the input.
* **Transformation Handling:** When the input undergoes transformations (e.g., `replace`, `replaceAll`, `trim`, `substring`), evaluate the exploit on the *transformed* data. If the transformation makes the payload ineffective, the vulnerability is not present.

**Decision Flow:**

1. **Identify Entry Points:** Analyze input parameters or external data sources that can reach the function.
2. **Trace Data Flow:** Follow the input to the vulnerable line. If it cannot reach, the vulnerability is not present.
3. **Check for Sanitization/Validation:** Identify and inspect processing applied to the input.
4. **Evaluate Effectiveness:** If a control fully blocks malicious input, the vulnerability is not present (stop analysis).
5. **Formulate Payload:** Only if no effective control exists, propose a payload that could exploit the vulnerability.
6. **Confirm Execution:** Show step-by-step how the payload is transformed, and confirm if it reaches the vulnerable code in an exploitable form.
7. **Final Decision:** Conclude whether the vulnerability is present. The vulnerability is present only if a valid payload exists that reaches the vulnerable line.

**Output Format:**

You must always reply with a valid JSON object with these fields and no more information:
* `"trace"`: A step-by-step explanation of your decision-making process. If the vulnerability is blocked early, explain why and stop.
* `"present"`: `"yes"` if the vulnerability is present, otherwise `"no"`.
* `"exploit"`: A payload string that can trigger the vulnerability if present. If `"present": "no"`, this must always be an empty string.
* `"reasoning_for_decision"`: A brief string that explains the final "yes" or "no" decision based on the rules.
"""
USER_PROMPT_CLASSIFIER_TEMPLATE = """
The Common Weakness Enumeration (CWE) description of the type of vulnerability identified is the following: `%s`.
The vulnerability identified by the scanner is the following: `%s`.
The vulnerability identified is located on line: `%s`.
The vulnerable source code is located in the file: `%s`.
"""


# ---------------------------------------
# Structure of the state that
# contain the information reed/create
# by nodes of the workflows
# ---------------------------------------
class WorkflowState(TypedDict):
    semgrep_record: dict
    cwe_id: int
    cwe_description: str
    source_file_path: str
    technology: str
    finding_description: str
    finding_start_line: int
    model_response: dict
    is_real_finding: str


# ---------------------------------------
# Utility functions
# ---------------------------------------
def ask_claude(user_prompt: str, call_options: ClaudeAgentOptions) -> str:
    async def _run():
        result = ""
        async for message in query(prompt=user_prompt, options=call_options):
            if hasattr(message, "result") and message.result:  # type: ignore
                result = message.result  # type: ignore
        result = result.replace("```json", "")
        result = result.replace("```", "")
        result = result.strip(" \r\n\t")
        print(result)
        return result

    return asyncio.run(_run())


# ---------------------------------------
# Nodes of the workflow (workers)
# ---------------------------------------
def parse_semgrep_record(state: WorkflowState) -> dict[str, Any]:
    updated_state_properties = {}
    data = state["semgrep_record"]
    # CWE-89: Improper Neutraliz....
    cwe_id = data["extra"]["metadata"]["cwe"][0]
    cwe_id = int(cwe_id.split(":")[0].split("-")[1].strip())
    source_file_path = data["path"]
    technology = data["extra"]["metadata"]["technology"][0]
    finding_description = data["extra"]["message"]
    finding_start_line = data["start"]["line"]
    updated_state_properties["cwe_id"] = cwe_id
    updated_state_properties["source_file_path"] = source_file_path
    updated_state_properties["technology"] = technology
    updated_state_properties["finding_description"] = finding_description
    updated_state_properties["finding_start_line"] = int(finding_start_line)
    return updated_state_properties


def retrieve_cwe_information(state: WorkflowState) -> dict[str, Any]:
    updated_state_properties = {}
    response = httpx.get(f"https://cwe-api.mitre.org/api/v1/cwe/weakness/{state['cwe_id']}")
    response.raise_for_status()
    response_json = response.json()
    cwe_description = response_json["Weaknesses"][0]["Description"]
    updated_state_properties["cwe_description"] = cwe_description.strip()
    return updated_state_properties


def classify_finding_with_llm(state: WorkflowState) -> dict[str, Any]:
    updated_state_properties = {}
    current_dir = Path.cwd()
    project_source_code_base_folder = f"{current_dir}/{VULNERABLE_CODEBASE_HOME}/{state['technology']}"
    claude_code_call_options = ClaudeAgentOptions(cwd=project_source_code_base_folder, allowed_tools=["Read", "Glob", "Grep"], permission_mode="dontAsk", system_prompt=SYSTEM_PROMPT_CLASSIFIER)
    # Prepare the user prompt passed to claude code
    cwe_desc = state["cwe_description"]
    finding_desc = state["finding_description"]
    finding_start_line = state["finding_start_line"]
    finding_file_path = f"{project_source_code_base_folder}/{state['source_file_path']}"
    user_prompt = USER_PROMPT_CLASSIFIER_TEMPLATE % (cwe_desc, finding_desc, finding_start_line, finding_file_path)
    # Call claude code
    model_response = ask_claude(user_prompt, claude_code_call_options)
    model_response_json = json.loads(model_response)
    # Add the response of claude code
    updated_state_properties["model_response"] = model_response_json
    updated_state_properties["is_real_finding"] = model_response_json["present"].title()
    return updated_state_properties


if __name__ == "__main__":
    # Assemble the agent
    ## Build the workflow
    agent_builder = StateGraph(WorkflowState)
    ## Add nodes
    agent_builder.add_node("parse_semgrep_record", parse_semgrep_record)
    agent_builder.add_node("retrieve_cwe_information", retrieve_cwe_information)
    agent_builder.add_node("classify_finding_with_llm", classify_finding_with_llm)
    ## Add connections between nodes (called Edge)
    agent_builder.add_edge(START, "parse_semgrep_record")
    agent_builder.add_edge("parse_semgrep_record", "retrieve_cwe_information")
    agent_builder.add_edge("retrieve_cwe_information", "classify_finding_with_llm")
    agent_builder.add_edge("classify_finding_with_llm", END)
    ## Compile the agent
    agent = agent_builder.compile()
    # Save a representation of the workflow
    with open(AGENT_MERMAID_IMAGE_FILE, "wb") as f:
        f.write(agent.get_graph(xray=True).draw_mermaid_png())
    # Load SemGrep test data
    finding_idx = 0
    if len(sys.argv) == 2:
        finding_idx = int(sys.argv[1])
    with open(SEMGREP_TEST_REPORT_FILE, mode="r", encoding=DEFAULT_ENCODING) as f:
        data = json.load(f)
    semgrep_record = data["results"][finding_idx]
    # Invoke the agent
    input = {"semgrep_record": semgrep_record}
    results = agent.invoke(cast(WorkflowState, input))
    for k, v in results.items():
        header = "=" * (len(k) * 2)
        state_key_name = k.replace("_", " ").title()
        print(header)
        print(f"ℹ️ {state_key_name}")
        print(header)
        print(v)
