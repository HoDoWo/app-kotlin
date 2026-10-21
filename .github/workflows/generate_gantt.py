import os
import requests

# GitHub API configuration
token = os.getenv("GH_TOKEN")
repo = os.getenv("REPOSITORY")

headers = {
    "Authorization": f"Bearer {token}",
    "Accept": "vnd.github+json"
}

# Example: Fetching closed/open issues from the repository
url = f"https://api.github.com/repos/{repo}/issues?state=all"
response = requests.get(url, headers=headers)
issues = response.json()

# Build Mermaid Gantt syntax
mermaid_content = """```mermaid
gantt
    title Planned vs Actual Schedule Comparison
    dateFormat  YYYY-MM-DD

    section Planned Schedule
"""

# Example mapping (you can parse custom fields or issue milestones here)
# For demonstration, we use fixed or milestone/due dates from issues
for issue in issues[:10]: # Limit to first 10 for example
    title = issue['title'].replace(":", "-")[:30]
    # In a full implementation, extract Planned Start/End and Actual Start/End from issue bodies or Projects v2 fields
    mermaid_content += f"    {title} (Plan) :p_{issue['number']}, 2026-10-05, 2026-10-20\n"

mermaid_content += "\n    section Actual Execution\n"

for issue in issues[:10]:
    title = issue['title'].replace(":", "-")[:30]
    mermaid_content += f"    {title} (Actual) :active, a_{issue['number']}, 2026-10-07, 2026-10-25\n"

mermaid_content += "```"

# Save to your documentation folder
os.makedirs("docs", exist_ok=True)
with open("docs/gantt-chart.md", "w") as f:
    f.write("# Project Gantt Comparison\n\n" + mermaid_content)

print("Gantt chart markdown successfully updated!")
