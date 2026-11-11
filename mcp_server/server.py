
import os
from datetime import date

import httpx
from mcp.server.fastmcp import FastMCP

SPRING_BOOT_URL = os.getenv(
    "SPRING_BOOT_URL",
    "https://nevertigo-mcp-backend.onrender.com"
).rstrip("/")

mcp = FastMCP(
    "NEVertigo MCP Server",
    host="0.0.0.0",
    port=int(os.getenv("PORT", "8000"))
)


def fetch_metric(endpoint: str, start: str, end: str) -> dict:
    """Fetch a verified metric from the Spring Boot backend."""

    start_date = date.fromisoformat(start)
    end_date = date.fromisoformat(end)

    if start_date > end_date:
        raise ValueError("Start date cannot be after end date.")

    with httpx.Client(timeout=60.0) as client:
        response = client.get(
            f"{SPRING_BOOT_URL}/metrics/{endpoint}",
            params={"start": start, "end": end}
        )
        response.raise_for_status()
        return response.json()


# 1. Total Visits
@mcp.tool()
def get_total_visits(start: str, end: str) -> dict:
    """Get total clinic visits between two dates (YYYY-MM-DD)."""
    return fetch_metric("visits", start, end)


# 2. New Patients
@mcp.tool()
def get_new_patients(start: str, end: str) -> dict:
    """Get new patient count between two dates (YYYY-MM-DD)."""
    return fetch_metric("new-patients", start, end)


# 3. Net Neuro Revenue
@mcp.tool()
def get_net_neuro_revenue(start: str, end: str) -> dict:
    """Get total net Neuro revenue between two dates (YYYY-MM-DD)."""
    return fetch_metric("net-neuro-revenue", start, end)


# 4. Patients With Visit
@mcp.tool()
def get_patients_with_visit(start: str, end: str) -> dict:
    """Get distinct patients with a visit between two dates."""
    return fetch_metric("patients-with-visit", start, end)


# 5. Follow-up Patients
@mcp.tool()
def get_followup_patients(start: str, end: str) -> dict:
    """Get patients who had a follow-up visit between two dates."""
    return fetch_metric("followup-patients", start, end)


# 6. BPPV Patients
@mcp.tool()
def get_bppv_patients(start: str, end: str) -> dict:
    """Get the count of BPPV patients between two dates."""
    return fetch_metric("bppv-patients", start, end)


# 7. BPPV Percentage
@mcp.tool()
def get_bppv_percentage(start: str, end: str) -> dict:
    """Get the BPPV patient percentage between two dates."""
    return fetch_metric("bppv-percentage", start, end)


# 8. Revenue By Scheme
@mcp.tool()
def get_revenue_by_scheme(start: str, end: str) -> dict:
    """Get net revenue split by CGHS and non-CGHS scheme."""
    return fetch_metric("revenue-by-scheme", start, end)


# 9. Revenue By Staff
@mcp.tool()
def get_revenue_by_staff(start: str, end: str) -> dict:
    """Get revenue attributed to the configured handlers and technicians."""
    return fetch_metric("revenue-by-staff", start, end)


# 10. Analysis Amount
@mcp.tool()
def get_analysis_amount(start: str, end: str) -> dict:
    """Get total analysis bill amount between two dates."""
    return fetch_metric("analysis-amount", start, end)


# 11. DVA Reports Received
@mcp.tool()
def get_dva_report_received(start: str, end: str) -> dict:
    """Get the count of patients with DVA reports received."""
    return fetch_metric("dva-report-received", start, end)


# 12. Medicine Lines
@mcp.tool()
def get_medicine_lines(start: str, end: str) -> dict:
    """Get the number of medicine lines prescribed between two dates."""
    return fetch_metric("medicine-line", start, end)


# 13. Low SPV Without Torsion
@mcp.tool()
def get_low_spv_no_torsion_visits(start: str, end: str) -> dict:
    """Get visits with low SPV and no torsion between two dates."""
    return fetch_metric("low-spv-no-torsion-visits", start, end)


if __name__ == "__main__":
    mcp.run(transport="streamable-http")
