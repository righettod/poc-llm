"""
Contains all tools definition put at disposal of a model used by a agent.

BaseModel for tool inputs/outputs:

- Pydantic models give you field descriptions that LangChain passes to the LLM as part of the tool schema
- The LLM uses those descriptions to understand what to pass in and how to interpret what comes back
- You also get runtime validation and clean attribute access
"""

from datetime import UTC, datetime
from typing import Literal

import httpx
from langchain_core.tools import tool
from pydantic import BaseModel, Field

from .constants import HTTP_REQUEST_TIMEOUT_IN_SECONDS
from .utils import get_access_token, is_in_scope

####
# Data container
####


class HttpRequestResult(BaseModel):
    timestamp: str = Field(description="ISO 8601 timestamp of when the request was sent")
    raw_request: str = Field(description="Full HTTP request as a string including method, path, headers and body")
    raw_response: str = Field(description="Full HTTP response as a string including status line, headers and body")
    status_code: int = Field(description="HTTP response status code, 0 if the request failed before receiving a response")
    response_body: str = Field(description="Decoded response body as a string, empty if no body or request failed")
    response_headers: dict = Field(description="Response headers as a key-value dict, empty if request failed")
    error: str | None = Field(default=None, description="Error type if the request failed — scope_violation, timeout, or network_error")
    error_reason: str | None = Field(default=None, description="Human readable explanation of the error, only present when error is set")


class OOBHit(BaseModel):
    timestamp: str = Field(description="ISO 8601 timestamp of when the OOB hit was received")
    payload: str = Field(description="The payload that triggered the OOB callback")
    callback_data: str = Field(description="Raw data received by the OOB listener on the callback")


class Request(BaseModel):
    sequence_no: int = Field(description="Sequential number of this request within the current test run")
    timestamp: str = Field(description="ISO 8601 timestamp of when the request was sent")
    intent: str = Field(description="Human-readable description of what this request is testing")
    auth_mode_used: Literal["authenticated", "anonymous"] = Field(description="Whether the request was sent with a bearer token or as an anonymous call")
    raw_request: str = Field(description="Full HTTP request as a string including method, path, headers and body")
    raw_response: str = Field(description="Full HTTP response as a string including status line, headers and body")


class Finding(BaseModel):
    id: str = Field(description="Unique identifier for this finding, e.g. FINDING-001")
    owasp_ref: str = Field(description="OWASP API Security Top 10 reference, e.g. API1:2023, or 'custom' / 'exploratory' for non-OWASP findings")
    severity: Literal["critical", "high", "medium", "low", "info"] = Field(description="Severity level of the finding")
    status: Literal["confirmed", "suspected"] = Field(description="Whether the finding is confirmed by clear evidence or only suspected")
    request_sequence_nos: list[int] = Field(description="Sequence numbers of the requests that produced or support this finding")
    reasoning: str = Field(description="Agent's explanation of why this is a finding, referencing the observed request and response")


class AssessmentTestResult(BaseModel):
    test_id: str = Field(description="Identifier of the test that was run, matching the test registry entry")
    test_name: str = Field(description="Human-readable name of the test, matching the test registry entry")
    source: Literal["owasp", "custom", "exploratory"] = Field(description="Origin of the test — from the OWASP list, a custom list, or invented by the agent during the run")
    auth_mode: Literal["authenticated", "anonymous", "both"] = Field(description="Auth mode used — 'both' means the test was run twice and responses were compared")
    status: Literal["executed", "blocked", "skipped"] = Field(description="Outcome of the test execution")
    block_reason: str | None = Field(default=None, description="Reason the test could not be executed — only present when status is 'blocked'")
    requests: list[Request] = Field(default_factory=list, description="All HTTP requests sent during this test, in sequence order")
    oob_hits: list[OOBHit] = Field(default_factory=list, description="OOB listener callbacks received during this test, if any")
    findings: list[Finding] = Field(default_factory=list, description="Findings produced by this test, empty if no issues were detected")


####
# Tools
####


@tool
def get_oob_listener_hits(payload: str = Field(description="The payload that triggered the hit")) -> list[OOBHit]:
    """
    Get the list of hits received by the out of band listener for a specific payload.
    When no hit was received then the list is empty.
    """
    return []


@tool
def send_http_request(
    method: str = Field(description="HTTP method — GET, POST, PUT, PATCH, DELETE"),
    path: str = Field(description="Request path — e.g. /api/v1/user/1"),
    host: str = Field(description="Request target host - e.g.www.example.com"),
    headers: dict = Field(description="HTTP headers as a key-value dict, excluding the auth header which is injected separately based on auth_mode"),
    body: str | None = Field(default=None, description="Request body as a string, None for requests with no body"),
    send_as_authenticated: bool = Field(
        default=False,
        description="Indicate if the request must be send as authenticated or as an anonymous",
    ),
) -> HttpRequestResult:
    """
    Send an HTTP request to the target service.
    Scope is enforced in code — only the target host is allowed.
    Returns the full request and response as strings for logging,
    or an error if the request was blocked or failed.
    Do not retry on error — record the result and move on.
    """
    current_datetime = datetime.now(UTC).isoformat()
    req_headers = {}
    req_headers.update(headers)
    if not is_in_scope(host):
        return HttpRequestResult(timestamp=datetime.now(UTC).isoformat(), raw_request="", raw_response="", status_code=0, error="scope_violation", error_reason=f"Host {host} is not the target scope!", response_body="", response_headers={})
    if send_as_authenticated:
        access_token = get_access_token()
        req_headers[access_token[0]] = access_token[1]

    url = f"https://{host}{path}"

    raw_request = f"{method} {path} HTTP/1.1\n"
    raw_request = f"Host: {host}\n"
    raw_request += "\n".join(f"{k}: {v}" for k, v in req_headers.items())
    if body:
        raw_request += f"\n\n{body}"
    response_headers = {}
    response_body = ""
    response_code = 0
    raw_response = ""

    try:
        with httpx.Client(timeout=HTTP_REQUEST_TIMEOUT_IN_SECONDS, follow_redirects=False) as client:
            request = client.build_request(
                method=method.upper(),
                url=url,
                headers=req_headers,
                content=body.encode() if body else None,
            )
            response = client.send(request)
            response_headers = dict(response.headers)
            response_body = response.text
            response_code = response.status_code

        raw_response = f"HTTP/1.1 {response.status_code} {response.reason_phrase}\n"
        raw_response += "\n".join(f"{k}: {v}" for k, v in response.headers.items())
        raw_response += f"\n\n{response.text}"

        return HttpRequestResult(timestamp=current_datetime, raw_request=raw_request, raw_response=raw_response, status_code=response_code, response_headers=response_headers, response_body=response_body)
    except httpx.TimeoutException:
        return HttpRequestResult(timestamp=current_datetime, raw_request=raw_request, raw_response=raw_response, error="timeout", error_reason=f"Request timed out after {HTTP_REQUEST_TIMEOUT_IN_SECONDS} seconds", status_code=response_code, response_headers=response_headers, response_body=response_body)
    except httpx.NetworkError as e:
        return HttpRequestResult(timestamp=current_datetime, raw_request=raw_request, raw_response=raw_response, error="network_error", error_reason=str(e), status_code=response_code, response_headers=response_headers, response_body=response_body)
