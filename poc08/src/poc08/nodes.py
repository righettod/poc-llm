""" "
Contains all nodes of the graph.

TypedDict for LangGraph state:

- LangGraph expects state to be a TypedDict
- It's a lightweight Python dict with type hints — no validation, no serialization overhead
- Designed for the graph infrastructure to pass data between nodes


Execute node
│
├── for each test in test_registry where status == "to_perform"
│   │
│   ├── 1. build user prompt (inject current test + progress summary)
│   ├── 2. invoke ReAct agent (system prompt + user prompt + 4 tools)
│   ├── 3. parse JSON output from agent
│   ├── 4. append requests to request_log in state
│   ├── 5. append findings to findings list in state
│   └── 6. mark test as executed / blocked / skipped in test_registry
│
└── when all tests done → pass to Validate node

"""

import uuid
from dataclasses import asdict, dataclass
from string import Template
from typing import TypedDict, cast

from langchain.agents import create_agent
from langchain_ollama import ChatOllama

from .constants import ASSESSMENT_AGENT_MODEL_CALL_TIMEOUT_IN_SECONDS, ASSESSMENT_AGENT_MODEL_NAME, ASSESSMENT_AGENT_MODEL_TEMPERATURE, ASSESSMENT_AGENT_SYSTEM_PROMPT, ASSESSMENT_AGENT_USER_PROMPT, DEBUG, OLLAMA_API_KEY, OLLAMA_HOST, SECURITY_TESTS_FILE
from .tools import AssessmentTestResult, get_oob_listener_hits, send_http_request
from .utils import load_base_request, load_security_tests

####
# Data container
####


@dataclass
class Finding(TypedDict):
    id: str
    owasp_ref: str
    severity: str
    status: str
    request_sequence_nos: list[int]
    reasoning: str


@dataclass
class SecurityTest(TypedDict):
    # Unique identifier
    id: str
    # Human friendly name
    name: str
    # List of findings found for this test
    findings: list[Finding]
    # Baseline severity, agent may adjust
    # critical / high / medium / low / info
    baseline_severity: str
    # Drives token usage; "both" means run twice and compare responses
    # authenticated / anonymous / both
    authentication_mode: str
    # Whether the OOB listener URL should be embedded in payloads
    oob_required: bool
    # Links finding to OWASP API TOP 10 taxonomy
    # API1:2023 or none
    owasp_ref: str
    # Test steps for the agent
    what_to_do: str
    # Success/failure indicators in the response
    what_to_look_for: str
    # Is error has occur during the execution of the test: HTTP 429, Request timeout, etc...
    has_error: bool
    error_details: str
    # Status
    # to_perform / executed / error
    status: str


@dataclass
class HttpRequest:
    target_host: str
    path: str
    http_version: str = "HTTP/1.1"
    id: str = str(uuid.uuid4())
    method: str = "GET"
    headers: dict[str, str] = {}
    body: str = ""

    def set_authentication_header(self, header_name: str, header_value: str) -> None:
        """Replace the Authorization header with a fresh bearer token."""
        self.headers[header_name] = header_value

    def to_dict(self) -> dict:
        """Convert to a plain JSON-serializable dict for LangGraph state storage."""
        return asdict(self)

    @classmethod
    def from_dict(cls, data: dict) -> HttpRequest:
        """Reconstruct from the dict stored in LangGraph state."""
        return cls(**data)


@dataclass
class WorkflowState(TypedDict):
    base_request_file_name: str
    base_request: HttpRequest
    oob_web_listener_url: str
    security_tests: list[SecurityTest]
    trace_log_file: str
    agent_assessment_system_prompt: str
    agent_assessment_user_prompt_template: str


####
# Nodes
####


def initialize(state: WorkflowState) -> WorkflowState:
    state["oob_web_listener_url"] = "https://righettod.eu"
    state["base_request"] = load_base_request(state["base_request_file_name"])
    state["trace_log_file"] = state["base_request_file_name"].split(".")[0].strip() + "_trace.log"
    state["security_tests"] = load_security_tests(SECURITY_TESTS_FILE)
    state["agent_assessment_system_prompt"] = ASSESSMENT_AGENT_SYSTEM_PROMPT
    state["agent_assessment_user_prompt_template"] = ASSESSMENT_AGENT_USER_PROMPT
    return state


def evaluate(state: WorkflowState) -> WorkflowState:
    # Model and agent init
    tools = [send_http_request, get_oob_listener_hits]
    llm = ChatOllama(
        model=ASSESSMENT_AGENT_MODEL_NAME,
        base_url=OLLAMA_HOST,
        client_kwargs={
            "headers": {"Authorization": f"Bearer {OLLAMA_API_KEY}"},
            "timeout": ASSESSMENT_AGENT_MODEL_CALL_TIMEOUT_IN_SECONDS,
        },
        temperature=ASSESSMENT_AGENT_MODEL_TEMPERATURE,
    )
    agent = create_agent(
        model=llm,
        tools=tools,
        system_prompt=state["agent_assessment_system_prompt"],
        debug=DEBUG,
        response_format=AssessmentTestResult,
    )
    user_prompt_template = Template(state["agent_assessment_user_prompt_template"])
    # Handle tests
    base_request = state["base_request"]
    security_tests = state["security_tests"]
    request_sequence = 1
    security_tests_progress_state = ""
    security_tests_performed_counter = 0
    findings_counter = 0
    security_tests_total = len(security_tests)
    for security_test in security_tests:
        test_section_markdown = f"### What to do:\n{security_test['what_to_do']}\n\n### What to look for:\n{security_test['what_to_look_for']}\n\n"
        user_prompt = user_prompt_template.substitute(
            method=base_request.method,
            path=base_request.path,
            host=base_request.target_host,
            headers=base_request.headers,
            body=base_request.body,
            authentication_mode=security_test["authentication_mode"],
            oob_listener_url=state["oob_web_listener_url"],
            test_section_markdown=test_section_markdown,
            tests_performed=security_tests_performed_counter,
            tests_total=security_tests_total,
            findings_count=findings_counter,
            next_sequence_no=request_sequence,
            progress_list=security_tests_progress_state,
        )
        result = agent.invoke({"messages": [{"role": "user", "content": user_prompt}]})
        assessment_result = cast(AssessmentTestResult, result)
        # Increments counters
        request_sequence += len(assessment_result.requests)
        current_security_test_finding_count = len(assessment_result.findings)
        security_tests_performed_counter += 1
        findings_counter += current_security_test_finding_count
        additional_info = ""
        if assessment_result.status == "blocked" and current_security_test_finding_count > 0:
            additional_info = assessment_result.block_reason
        elif current_security_test_finding_count > 0:
            additional_info = f"{current_security_test_finding_count} finding detected"
        else:
            additional_info = "no finding"
        security_tests_progress_state += f"{security_test['name']} - {assessment_result.status} - {additional_info}\n"
        # TODO handle the response from the state and trace perspective

    return state
