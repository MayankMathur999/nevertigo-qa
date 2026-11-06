
import os
from datetime import date

import httpx
from mcp.server.fastmcp import FastMCP

mcp = FastMCP("NEVertigo MCP Server")
SPRING_BOOT_URL = os.getenv(
    "SPRING_BOOT_URL",
    "http://127.0.0.1:8081"
)

def fetch_metric(endpoint: str, start: str, end: str) -> dict:
    """Fetch a verified metric from our Spring Boot backend."""

    start_date = date.fromisoformat(start)
    end_date = date.fromisoformat(end)

    if start_date > end_date:
        raise ValueError("Start date cannot be after end date.")

    with httpx.Client(timeout=15.0) as client:
        response = client.get(
            f"{SPRING_BOOT_URL}/metrics/{endpoint}",
            params={"start": start, "end": end}
        )
        response.raise_for_status()
        return response.json()


@mcp.tool()
def get_total_visits(start: str, end: str) -> dict:
    """Get total clinic visits between two dates (YYYY-MM-DD)."""
    return fetch_metric("visits", start, end)


@mcp.tool()
def get_new_patients(start: str, end: str) -> dict:
    """Get new patient count between two dates (YYYY-MM-DD)."""
    return fetch_metric("new-patients", start, end)


@mcp.tool()
def get_net_neuro_revenue(start: str, end: str) -> dict:
    """Get total net Neuro revenue between two dates (YYYY-MM-DD)."""
    return fetch_metric("net-neuro-revenue", start, end)


if __name__ == "__main__":
    mcp.run(transport="streamable-http")
