import os
import requests
import json

token = os.getenv("GH_TOKEN")
repository = os.getenv("REPOSITORY", "HoDoWo/app-kotlin")
owner, repo_name = repository.split("/")

headers = {
    "Authorization": f"Bearer {token}",
    "Content-Type": "application/json",
}

# GraphQL query to fetch GitHub Project v2 items and their custom fields
# Adjust projectNumber if your project number is different (e.g., 1, 2...)
graphql_query = """
query($owner: String!, $repo: String!) {
  repository(owner: $owner, name: $repo) {
    projectsV2(first: 5) {
      nodes {
        title
        number
        items(first: 100) {
          nodes {
            content {
              ... on Issue {
                title
                number
              }
            }
            fieldValues(first: 20) {
              nodes {
                ... on ProjectV2ItemFieldDateValue {
                  date
                  field {
                    ... on ProjectV2FieldCommon { name }
                  }
                }
                ... on ProjectV2ItemFieldTextValue {
                  text
                  field {
                    ... on ProjectV2FieldCommon { name }
                  }
                }
                ... on ProjectV2ItemFieldNumberValue {
                  number
                  field {
                    ... on ProjectV2FieldCommon { name }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
"""

response = requests.post(
    "https://api.github.com/graphql",
    json={"query": graphql_query, "variables": {"owner": owner, "repo": repo_name}},
    headers=headers
)

if response.status_code != 200:
    raise Exception(f"GraphQL Query failed: {response.status_code} - {response.text}")

data = response.json()
if "errors" in data:
    raise Exception(f"GraphQL Errors: {data['errors']}")

projects = data["data"]["repository"]["projectsV2"]["nodes"]
if not projects:
    # An empty list usually means the token cannot read the Project (GITHUB_TOKEN never can).
    raise SystemExit("No GitHub Projects v2 visible for this repository: check that the "
                     "PROJECT_TOKEN secret has the read:project scope and that the Project is linked to the repository.")

# Use the first project found
project = projects[0]
print(f"Parsing project: {project['title']}")

mermaid_planned = ""
mermaid_actual = ""

for item in project["items"]["nodes"]:
    content = item.get("content")
    if not content or "title" not in content:
        continue
    
    title = f"#{content['number']} {content['title']}".replace(":", "-")[:35]
    
    # Extract field values
    fields = {}
    for fv in item["fieldValues"]["nodes"]:
        field_info = fv.get("field")
        if field_info and "name" in field_info:
            name = field_info["name"]
            if "date" in fv:
                fields[name] = fv["date"]
            elif "text" in fv:
                fields[name] = fv["text"]
            elif "number" in fv:
                fields[name] = fv["number"]

    est_start = fields.get("Estimated Start Date")
    est_end = fields.get("Estimated End Date")
    act_start = fields.get("Start Date")
    act_end = fields.get("End Date")

    # Fallbacks if dates are missing
    if est_start and est_end:
        mermaid_planned += f"    {title} (Plan) :p_{content['number']}, {est_start}, {est_end}\n"
    
    if act_start and act_end:
        mermaid_actual += f"    {title} (Actual) :active, a_{content['number']}, {act_start}, {act_end}\n"

# Assemble markdown file with Mermaid Gantt chart
mermaid_content = f"""```mermaid
gantt
    title Project Schedule Comparison: Planned vs Actual
    dateFormat  YYYY-MM-DD

    section Estimated (Baseline)
{mermaid_planned if mermaid_planned else "    No estimated dates found : 2026-10-06, 2026-10-26"}

    section Actual Execution
{mermaid_actual if mermaid_actual else "    No actual dates found : 2026-10-06, 2026-10-26"}
```"""

os.makedirs("docs", exist_ok=True)
with open("docs/gantt-chart.md", "w") as f:
    f.write("# Project Gantt Comparison\n\n" + mermaid_content)

print("Gantt comparison chart successfully generated from Project fields!")
