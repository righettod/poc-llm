"""
Contains all tools definition put at disposal of a model used by a agent.

BaseModel for tool inputs/outputs:

- Pydantic models give you field descriptions that LangChain passes to the LLM as part of the tool schema
- The LLM uses those descriptions to understand what to pass in and how to interpret what comes back
- You also get runtime validation and clean attribute access
"""

from datetime import datetime, timezone

import httpx
from constants import HTTP_REQUEST_TIMEOUT_IN_SECONDS
from langchain_core.tools import tool
from pydantic import BaseModel, Field
from utils import is_in_scope

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


class AccessTokenResult(BaseModel):
    auth_header_name: str = Field(description="Name of the HTTP header carrying the authentication value — e.g. Authorization, Cookie, X-Api-Key")
    auth_header_value: str = Field(description="Full header value formatted and ready to use — e.g. Bearer eyJ..., session=abc123")
    error: str | None = Field(default=None, description="Error type if the token could not be fetched")
    error_reason: str | None = Field(default=None, description="Human readable explanation of the error, only present when error is set")


class SecurityTest(BaseModel):
    test_id: str = Field(description="Unique identifier for the test — derived from the markdown section heading")
    test_name: str = Field(description="Human readable name of the test")
    source: str = Field(description="Origin of the test — owasp or custom")
    owasp_ref: str = Field(description="OWASP API Top 10 reference — e.g. API1:2023, or none if not applicable")
    severity: str = Field(description="Baseline severity — critical, high, medium, low, or info")
    auth_mode: str = Field(description="Authentication mode — authenticated, anonymous, or both")
    oob_required: bool = Field(description="Whether the OOB listener URL should be embedded in payloads for this test")
    what_to_do: str = Field(description="Test steps for the agent")
    what_to_look_for: str = Field(description="Success or failure indicators to observe in the response")
    section_markdown: str = Field(description="Full raw markdown section for this test as it appears in the test file")


class SecurityTestsResult(BaseModel):
    tests: list[SecurityTest] = Field(description="Ordered list of security tests parsed from the markdown test file")
    error: str | None = Field(default=None, description="Error type if the test file could not be loaded or parsed")
    error_reason: str | None = Field(default=None, description="Human readable explanation of the error, only present when error is set")


class OOBHit(BaseModel):
    timestamp: str = Field(description="ISO 8601 timestamp of when the callback was received")
    source_ip: str = Field(description="IP address that triggered the OOB callback")
    payload: str = Field(description="The payload that triggered the hit")
    callback_data: str = Field(description="Raw data received by the OOB listener")


class OOBHitsResult(BaseModel):
    hits: list[OOBHit] = Field(description="List of OOB callbacks received since the last check, empty if none")
    error: str | None = Field(default=None, description="Error type if the OOB listener could not be reached")
    error_reason: str | None = Field(default=None, description="Human readable explanation of the error, only present when error is set")


####
# Tools
####


@tool
def get_access_token() -> AccessTokenResult:
    """
    Fetch a fresh authentication token for the target service.
    Returns the header name and fully formatted header value ready to use in requests.
    Call this once at the start — the result is valid for the duration of the assessment.
    """
    return AccessTokenResult(auth_header_name="Authorization", auth_header_value="Bearer ABCDEF")


@tool
def send_http_request(
    method: str = Field(description="HTTP method — GET, POST, PUT, PATCH, DELETE"),
    path: str = Field(description="Request path — e.g. /api/v1/user/1"),
    host: str = Field(description="Request target host - e.g.www.example.com"),
    headers: dict = Field(description="HTTP headers as a key-value dict, excluding the auth header which is injected separately based on auth_mode"),
    body: str | None = Field(default=None, description="Request body as a string, None for requests with no body"),
) -> HttpRequestResult:
    """
    Send an HTTP request to the target service.
    Scope is enforced in code — only the target host is allowed.
    Returns the full request and response as strings for logging,
    or an error if the request was blocked or failed.
    Do not retry on error — record the result and move on.
    """
    current_datetime = datetime.now(timezone.utc).isoformat()
    if not is_in_scope(host):
        return HttpRequestResult(timestamp=datetime.now(timezone.utc).isoformat(), raw_request="", raw_response="", status_code=0, error="scope_violation", error_reason=f"Host {host} is not the target scope!", response_body="", response_headers={})

    url = f"https://{host}{path}"

    raw_request = f"{method} {path} HTTP/1.1\n"
    raw_request = f"Host: {host}\n"
    raw_request += "\n".join(f"{k}: {v}" for k, v in headers.items())
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
                headers=headers,
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
