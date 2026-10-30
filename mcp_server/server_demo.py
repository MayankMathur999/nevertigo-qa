
import os
from mcp.server.fastmcp import FastMCP

mcp = FastMCP(
    "NEVertigo Demo MCP Server",
    host="0.0.0.0",
    port=int(os.getenv("PORT", "8000"))
)

@mcp.tool()
def get_total_visits(start: str, end: str) -> dict:
    """Get demo clinic visits for a date range."""
    return {
        "metric": "visits",
        "start": start,
        "end": end,
        "value": 8,
        "data_source": "DEMO_ONLY"
    }

@mcp.tool()
def get_new_patients(start: str, end: str) -> dict:
    """Get demo new patients for a date range."""
    return {
        "metric": "new_patients",
        "start": start,
        "end": end,
        "value": 5,
        "data_source": "DEMO_ONLY"
    }

@mcp.tool()
def get_net_neuro_revenue(start: str, end: str) -> dict:
    """Get demo Neuro revenue for a date range."""
    return {
        "metric": "net_neuro_revenue",
        "start": start,
        "end": end,
        "value": 11600,
        "data_source": "DEMO_ONLY"
    }

if __name__ == "__main__":
    mcp.run(transport="streamable-http")
