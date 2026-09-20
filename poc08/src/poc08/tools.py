"""
Contains all tools definition put at disposal of a model used by a agent.

BaseModel for tool inputs/outputs:

- Pydantic models give you field descriptions that LangChain passes to the LLM as part of the tool schema
- The LLM uses those descriptions to understand what to pass in and how to interpret what comes back
- You also get runtime validation and clean attribute access
"""

from pydantic import BaseModel, Field

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
