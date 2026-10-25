"""
Contains the implementation of the idea as simple and single script divided into sections.
"""

import json
import os
import re
import uuid
from dataclasses import asdict, dataclass
from datetime import UTC, datetime
from pathlib import Path
from string import Template
from typing import Literal, TypedDict, cast

import httpx
from langchain.agents import create_agent
from langchain_core.tools import tool
from langchain_ollama import ChatOllama
from pydantic import BaseModel, Field

#######################################################################
# Constants
#######################################################################
DEBUG = True
OLLAMA_API_KEY = os.environ.get("OLLAMA_API_KEY")
DEFAULT_ENCODING = "utf-8"
OLLAMA_HOST = "https://ollama.com"
SECURITY_TESTS_FILE = "security_tests.md"
TRACE_LOG_FILE = "trace.log"
HTTP_REQUEST_TIMEOUT_IN_SECONDS = 10
ASSESSMENT_AGENT_MODEL_NAME = ""
ASSESSMENT_AGENT_MODEL_TEMPERATURE = 0.1
ASSESSMENT_AGENT_MODEL_CALL_TIMEOUT_IN_SECONDS = 240

#######################################################################
# System and User Prompts
#######################################################################
ASSESSMENT_AGENT_SYSTEM_PROMPT = """
You are an autonomous REST API security assessment agent. Your sole purpose 
is to execute security tests against a target REST API service, observe 
responses, and record structured findings for downstream validation.

## Behaviour Rules

1. **Scope** — only send requests to the target host. The request tool 
   enforces this hard limit. Never attempt to contact any other host directly.

2. **Coverage** — you must attempt every test provided. You may reorder 
   freely based on what you observe, but every test must end with a status: 
   `executed`, `blocked`, or `skipped`.

3. **No retries** — if a request times out, returns a 429, or fails with a 
   network error, mark the test as `blocked` with the reason and move on 
   immediately.

4. **Authentication** — strictly follow the `Authentication` instruction of each test:
   - `authenticated`: include the bearer token in the Authorization header
   - `anonymous`: omit the Authorization header entirely
   - `both`: run the test twice — once anonymous, once authenticated — 
     then compare responses

5. **Both mode comparison** — flag as a finding any case where the anonymous 
   and authenticated responses are equivalent when they should differ, or 
   where the anonymous response returns data that should require authentication.

6. **Exploration** — if you identify an additional attack surface not covered 
   by the test list, add it as an exploratory test and execute it. Tag its 
   source as `exploratory`.

7. **No assumptions** — do not assume a vulnerability exists without evidence 
   from a response. Do not assume a control is in place without 
   testing it.

## When to Raise a Finding

Raise a finding when you observe:
- Data or functionality exposed beyond what the authenticated user should access
- No meaningful difference between authenticated and anonymous responses 
  when one is expected
- A server-side callback registered on the OOB listener
- Error messages disclosing internal details: stack traces, SQL errors, 
  file paths, framework versions
- Evidence of successful injection: SQL, NoSQL, command, template, header, 
  or path injection
- Any behaviour indicating a security control is missing, misconfigured, 
  or bypassable

Classify each finding as:
- `confirmed` — clear evidence from the response or OOB hit
- `suspected` — anomalous behaviour that suggests a vulnerability but 
  cannot be fully confirmed

## Output Format

After completing a test, output a single valid JSON object and nothing else.
No prose, no explanation, no markdown around it.
The Execute node parses this output directly.

{
  "test_id": "string — identifier from the test definition",
  "test_name": "string — name of the test",
  "source": "provided | exploratory",
  "auth_mode": "authenticated | anonymous | both",
  "status": "executed | error",
  "block_reason": "string — only present if status is error",
  "requests": [
    {
      "id": 1,
      "timestamp": "ISO 8601 — e.g. 2026-09-19T14:32:07Z",
      "auth_mode_used": "authenticated | anonymous",
      "raw_request": "string — full HTTP request including method, path, headers and body",
      "raw_response": "string — full HTTP response including status line, headers and body"
    }
  ],
  "findings": [
    {
      "id": 1,
      "severity": "critical | high | medium | low | info",
      "status": "confirmed | suspected",
      "request_sequence_ids": [1, 2],
      "reasoning": "string — why this is flagged, what evidence supports it"
    }
  ]
}

Rules for the JSON output:
- `findings` is always present — empty array if no finding was raised
- `block_reason` is only present when `status` is `error`
- `raw_request` and `raw_response` are plain strings with embedded newlines
"""
ASSESSMENT_AGENT_USER_PROMPT = """
## Target

Method: $method
Path: $path
Host: $host
Headers:
$headers

Body:
$body

Authentication: $authentication_mode

## Current Test

$test_section_markdown
"""


#######################################################################
# Data container used by the program to carry data
#######################################################################


@dataclass
class SecurityTestBaseHttpRequest:
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
    def from_dict(cls, data: dict):
        """Reconstruct from the dict stored in LangGraph state."""
        return cls(**data)


@dataclass
class SecurityTestFinding(TypedDict):
    id: str
    severity: str
    status: str
    reasoning: str
    http_exchanges: list[tuple[str, str]]  # a tuple is composed by a raw request and its raw related response


@dataclass
class SecurityTest(TypedDict):
    # Unique identifier
    id: str
    # Human friendly name
    name: str
    # List of findings found for this test
    findings: list[SecurityTestFinding]
    # Baseline severity, agent may adjust
    # critical / high / medium / low / info
    baseline_severity: str
    # Drives token usage; "both" means run twice and compare responses
    # authenticated / anonymous / both
    authentication_mode: str
    # Test steps for the agent
    what_to_do: str
    # Success/failure indicators in the response
    what_to_look_for: str
    # Is error has occur during the execution of the test: HTTP 429, Request timeout, etc...
    error_details: str | None
    # Status
    # to_perform / executed / error
    status: str


@dataclass
class WorkflowState(TypedDict):
    base_request_file_name: str
    base_request: SecurityTestBaseHttpRequest
    security_tests: list[SecurityTest]
    agent_assessment_system_prompt: str
    agent_assessment_user_prompt_template: str


#######################################################################
# Data cotnainer used by the model and tools to carry data
#######################################################################


class AssessmentRequestExecution(BaseModel):
    id: int = Field(description="Sequential request number starting at 1")
    timestamp: str = Field(description="ISO 8601 timestamp of when the request was sent")
    auth_mode_used: Literal["authenticated", "anonymous"] = Field(description="Whether the request was sent with a bearer token or as an anonymous call")
    raw_request: str = Field(description="Full HTTP request as a string including method, path, headers and body")
    raw_response: str = Field(description="Full HTTP response as a string including status line, headers and body")
    error_reason: str = Field(description="Error description if the resquest sending failed. Empty when request was sent successfully")


class AssessmentFinding(BaseModel):
    id: int = Field(description="Sequential finding number starting at 1")
    severity: Literal["critical", "high", "medium", "low", "info"] = Field(description="Severity level of the finding")
    status: Literal["confirmed", "suspected"] = Field(description="Whether the finding is confirmed by clear evidence or only suspected")
    request_sequence_ids: list[int] = Field(description="Sequence numbers of the requests that produced or support this finding")
    reasoning: str = Field(description="Agent's explanation of why this is a finding, referencing the observed request and response")


class AssessmentTestResult(BaseModel):
    test_id: str = Field(description="Identifier of the test that was run, matching the test registry entry")
    test_name: str = Field(description="Human-readable name of the test, matching the test registry entry")
    source: Literal["provided", "exploratory"] = Field(description="Origin of the test — from the provided test to perform, or invented by the agent during the run")
    auth_mode: Literal["authenticated", "anonymous", "both"] = Field(description="Auth mode used — 'both' means the test was run twice and responses were compared")
    status: Literal["executed", "error"] = Field(description="Outcome of the test execution")
    block_reason: str | None = Field(default=None, description="Reason the test could not be executed — only present when status is 'error'")
    requests: list[AssessmentRequestExecution] = Field(default_factory=list, description="All HTTP requests sent during this test, in sequence order")
    findings: list[AssessmentFinding] = Field(default_factory=list, description="Findings produced by this test, empty if no issues were detected")


#######################################################################
# Utilities functions
#######################################################################


def is_in_scope(host: str) -> bool:
    """
    Indicate if the provided host is part of the scope of the test
    """
    return True


def load_base_request(file_path: str) -> SecurityTestBaseHttpRequest:
    """Parse a raw HTTP request file (Burp Suite export compatible) into a plain dict.

    The returned dict is object representing the request properties.

    Expected format:
        GET /api/v1/user/1 HTTP/1.1
        Host: example.com
        Authorization: Bearer XXXXX
        ...

        <optional body>

    Raises:
        FileNotFoundError: if the file does not exist.
        ValueError: if the first line cannot be parsed as a valid HTTP request line.
    """
    path = Path(file_path)
    if not path.exists():
        raise FileNotFoundError(f"HTTP request file not found: {file_path}")

    raw = path.read_text(encoding="utf-8")
    lines = raw.splitlines()

    if not lines:
        raise ValueError("HTTP request file is empty.")

    # --- line 1: method, path, HTTP version ---
    parts = lines[0].strip().split(" ", 2)
    if len(parts) < 2:
        raise ValueError(f"Cannot parse HTTP request line: {lines[0]!r}")

    method = parts[0].upper()
    request_path = parts[1]
    http_version = parts[2] if len(parts) == 3 else "HTTP/1.1"

    # --- headers and body ---
    headers: dict[str, str] = {}
    body_lines: list[str] = []
    in_body = False

    for line in lines[1:]:
        if not in_body and line.strip() == "":
            in_body = True
            continue

        if in_body:
            body_lines.append(line)
        else:
            if ":" in line:
                name, _, value = line.partition(":")
                headers[name.strip()] = value.strip()

    body = "\n".join(body_lines).strip()

    # --- extract target host ---
    target_host = headers.get("Host", "")

    req_data = {"method": method, "path": request_path, "http_version": http_version, "headers": headers, "body": body, "target_host": target_host, "id": str(uuid.uuid4())}
    request = SecurityTestBaseHttpRequest.from_dict(req_data)
    return request


def load_security_tests(file_path: str) -> list[SecurityTest]:
    """Parse security_tests.md and return one SecurityTest per H1 (#) section.

    Each H1 heading is a test name. H2 (##) sub-sections carry the field values.

    Raises:
        FileNotFoundError: if the file does not exist.
    """
    path = Path(file_path)
    if not path.exists():
        raise FileNotFoundError(f"Security tests file not found: {file_path}")

    text = path.read_text(encoding="utf-8")

    # Split on H1 headings; the regex keeps nothing before the first heading
    raw_sections = re.split(r"(?m)^#(?!#)\s+", text)

    tests: list[SecurityTest] = []

    for raw in raw_sections:
        raw = raw.strip()
        if not raw:
            continue

        lines = raw.splitlines()
        name = lines[0].strip()
        body = "\n".join(lines[1:])

        def _extract(heading: str) -> str:
            match = re.search(
                rf"##\s+{re.escape(heading)}\s*\n(.*?)(?=\n##\s|\Z)",
                body,
                re.IGNORECASE | re.DOTALL,
            )
            return match.group(1).strip() if match else ""

        severity_raw = _extract("Baseline severity")
        auth_mode_raw = _extract("Authentication mode")

        tests.append(
            SecurityTest(
                id=re.sub(r"[^a-zA-Z0-9]+", "-", name.lower()).strip("-"),
                name=name,
                findings=[],
                baseline_severity=severity_raw.lower() if severity_raw else "info",
                authentication_mode=auth_mode_raw.lower() if auth_mode_raw else "authenticated",
                what_to_do=_extract("What to do"),
                what_to_look_for=_extract("What to look for"),
                error_details="",
                status="to_perform",
            )
        )

    return tests


def get_access_token() -> tuple[str, str]:
    return ("Authorization", "Bearer ABCDEF")


def add_test_to_trace_log(test_data: AssessmentTestResult):
    trace_data = []
    if os.path.exists(TRACE_LOG_FILE):
        with open(TRACE_LOG_FILE, "r", encoding=DEFAULT_ENCODING) as f:
            trace_data = json.load(f)
    trace_data.append(test_data.model_dump())
    with open(TRACE_LOG_FILE, "w", encoding=DEFAULT_ENCODING) as f:
        json.dump(trace_data, f, indent=2)


#######################################################################
# Tools made available to the model
#######################################################################


@tool
def send_http_request(
    request_id: int = Field(description="Sequence identifier of the request"),
    method: str = Field(description="HTTP method — GET, POST, PUT, PATCH, DELETE"),
    path: str = Field(description="Request path — e.g. /api/v1/user/1"),
    host: str = Field(description="Request target host - e.g.www.example.com"),
    headers: dict = Field(description="HTTP headers as a key-value dict, excluding the auth header which is injected separately based on auth_mode"),
    body: str | None = Field(default=None, description="Request body as a string, None for requests with no body"),
    send_as_authenticated: bool = Field(
        default=False,
        description="Indicate if the request must be send as authenticated or as an anonymous",
    ),
) -> AssessmentRequestExecution:
    """
    Send an HTTP request to the target service.
    Scope is enforced in code — only the target host is allowed.
    Returns the full request and response as strings for logging,
    or an error if the request was blocked or failed.
    Do not retry on error — record the result and move on.
    """
    req_id = request_id
    current_datetime = datetime.now(UTC).isoformat()
    auth_mode_used = "authenticated" if send_as_authenticated else "anonymous"
    raw_request = ""
    raw_response = ""
    req_headers = {}
    req_headers.update(headers)
    if not is_in_scope(host):
        return AssessmentRequestExecution(id=req_id, timestamp=datetime.now(UTC).isoformat(), auth_mode_used=auth_mode_used, raw_request="", raw_response="", error_reason=f"Host {host} is not the target scope!")
    if send_as_authenticated:
        access_token = get_access_token()
        req_headers[access_token[0]] = access_token[1]

    url = f"https://{host}{path}"

    raw_request = f"{method} {path} HTTP/1.1\n"
    raw_request = f"Host: {host}\n"
    raw_request += "\n".join(f"{k}: {v}" for k, v in req_headers.items())
    if body:
        raw_request += f"\n\n{body}"

    try:
        with httpx.Client(timeout=HTTP_REQUEST_TIMEOUT_IN_SECONDS, follow_redirects=False) as client:
            request = client.build_request(
                method=method.upper(),
                url=url,
                headers=req_headers,
                content=body.encode() if body else None,
            )
            response = client.send(request)
        raw_response = f"HTTP/1.1 {response.status_code} {response.reason_phrase}\n"
        raw_response += "\n".join(f"{k}: {v}" for k, v in response.headers.items())
        raw_response += f"\n\n{response.text}"

        return AssessmentRequestExecution(id=req_id, timestamp=current_datetime, auth_mode_used=auth_mode_used, raw_request=raw_request, raw_response=raw_response, error_reason="")
    except httpx.TimeoutException:
        return AssessmentRequestExecution(id=req_id, timestamp=current_datetime, auth_mode_used=auth_mode_used, raw_request=raw_request, raw_response=raw_response, error_reason=f"Request timed out after {HTTP_REQUEST_TIMEOUT_IN_SECONDS} seconds.")
    except httpx.NetworkError as e:
        return AssessmentRequestExecution(id=req_id, timestamp=current_datetime, auth_mode_used=auth_mode_used, raw_request=raw_request, raw_response=raw_response, error_reason=f"A network error occur: {e!s}.")
    except Exception as e:
        return AssessmentRequestExecution(id=req_id, timestamp=current_datetime, auth_mode_used=auth_mode_used, raw_request=raw_request, raw_response=raw_response, error_reason=f"A non expected error occur: {e!s}.")


#######################################################################
# Nodes for the LangGrah graph representing the agent processing flow
#######################################################################


def initialize(state: WorkflowState) -> WorkflowState:
    state["base_request"] = load_base_request(state["base_request_file_name"])
    state["security_tests"] = load_security_tests(SECURITY_TESTS_FILE)
    state["agent_assessment_system_prompt"] = ASSESSMENT_AGENT_SYSTEM_PROMPT
    state["agent_assessment_user_prompt_template"] = ASSESSMENT_AGENT_USER_PROMPT
    return state


def evaluate(state: WorkflowState) -> WorkflowState:
    # Model and agent init
    tools = [send_http_request]
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
    security_test_base_request = state["base_request"]
    security_tests = state["security_tests"]
    for security_test in security_tests:
        # Run the current security test
        test_section_markdown = f"### What to do:\n{security_test['what_to_do']}\n\n### What to look for:\n{security_test['what_to_look_for']}\n\n"
        user_prompt = user_prompt_template.substitute(
            method=security_test_base_request.method,
            path=security_test_base_request.path,
            host=security_test_base_request.target_host,
            headers=security_test_base_request.headers,
            body=security_test_base_request.body,
            authentication_mode=security_test["authentication_mode"],
            test_section_markdown=test_section_markdown,
        )
        result = agent.invoke({"messages": [{"role": "user", "content": user_prompt}]})
        assessment_result = cast(AssessmentTestResult, result)
        # Log the security test execution to the trace log
        add_test_to_trace_log(assessment_result)
        # Update content of the security test with the output of the model
        security_test["status"] = assessment_result.status
        security_test["error_details"] = assessment_result.block_reason
        for assessment_result_finding in assessment_result.findings:
            security_test_finding_id = str(uuid.uuid4())
            # TODO determine the severity of the security test based on the collection of severity of the collection of findings
            security_test_finding_severity = assessment_result_finding.severity
            security_test_finding_status = assessment_result_finding.status
            security_test_finding_reasoning = assessment_result_finding.reasoning
            security_test_finding_exchanges = []
            model_requests_list = assessment_result.requests
            for security_test_finding_request_sequence_id in assessment_result_finding.request_sequence_ids:
                security_test_finding_exchanges.append((model_requests_list[security_test_finding_request_sequence_id].raw_request, model_requests_list[security_test_finding_request_sequence_id].raw_response))
            security_test_finding = SecurityTestFinding(id=security_test_finding_id, severity=security_test_finding_severity, status=security_test_finding_status, reasoning=security_test_finding_reasoning, http_exchanges=security_test_finding_exchanges)
            security_test["findings"].append(security_test_finding)

    return state


# TODO finalize nodes graph and program and start testing it
