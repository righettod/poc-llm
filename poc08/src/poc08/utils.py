"""
Contains all utility functions used by the nodes of the graph.
"""

import re
import uuid
from pathlib import Path

from .nodes import HttpRequest, SecurityTest


def is_in_scope(host: str) -> bool:
    """
    Indicate if the provided host is part of the scope of the test
    """
    return True


def load_base_request(file_path: str) -> HttpRequest:
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
    request = HttpRequest.from_dict(req_data)
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

        owasp_ref = _extract("OWASP reference")
        severity_raw = _extract("Baseline severity")
        auth_mode_raw = _extract("Authentication mode")
        oob_raw = _extract("OOB required")

        tests.append(
            SecurityTest(
                id=re.sub(r"[^a-zA-Z0-9]+", "-", name.lower()).strip("-"),
                name=name,
                findings=[],
                baseline_severity=severity_raw.lower() if severity_raw else "info",
                authentication_mode=auth_mode_raw.lower() if auth_mode_raw else "authenticated",
                oob_required=oob_raw.strip().lower() in ("true", "yes", "1"),
                owasp_ref=owasp_ref if owasp_ref else "none",
                what_to_do=_extract("What to do"),
                what_to_look_for=_extract("What to look for"),
                has_error=False,
                error_details="",
                status="to_perform",
            )
        )

    return tests


def get_access_token() -> tuple[str, str]:
    return ("Authorization", "Bearer ABCDEF")
