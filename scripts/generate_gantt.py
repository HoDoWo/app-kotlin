import os
import requests
import json

token = os.getenv("GH_TOKEN")
repository = os.getenv("REPOSITORY", "HoDoWo/app-kotlin")
target_sprint = os.getenv("TARGET_SPRINT", "").strip()
owner, repo_name = repository.split("/")

headers = {
    "Authorization": f"Bearer {token}",
    "Content-Type": "application/json",
}

# Consulta GraphQL ampliada para capturar Iteraciones y Campos de Selección
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
            fieldValues(first: 100) {
              nodes {
                ... on ProjectV2ItemFieldDateValue {
                  date
                  field { ... on ProjectV2FieldCommon { name } }
                }
                ... on ProjectV2ItemFieldTextValue {
                  text
                  field { ... on ProjectV2FieldCommon { name } }
                }
                ... on ProjectV2ItemFieldNumberValue {
                  number
                  field { ... on ProjectV2FieldCommon { name } }
                }
                ... on ProjectV2ItemFieldIterationValue {
                  title
                  field { ... on ProjectV2FieldCommon { name } }
                }
                ... on ProjectV2ItemFieldSingleSelectValue {
                  name
                  field { ... on ProjectV2FieldCommon { name } }
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
    raise SystemExit("No GitHub Projects v2 visible for this repository.")

project = projects[0]
print(f"Parsing project: {project['title']}")
if target_sprint:
    print(f"Filtering by Sprint: {target_sprint}")

mermaid_planned = ""
mermaid_actual = ""
matched_count = 0

for item in project["items"]["nodes"]:
    content = item.get("content")
    if not content or "title" not in content:
        continue
    
    fields = {}
    item_sprint = None

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
            elif "title" in fv:  # Para campos tipo Iteration (Sprints nativos)
                fields[name] = fv["title"]
                if "sprint" in name.lower() or "iteration" in name.lower():
                    item_sprint = fv["title"]
            elif "name" in fv:  # Para campos Single Select
                fields[name] = fv["name"]
                if "sprint" in name.lower() or "iteration" in name.lower():
                    item_sprint = fv["name"]

    # Fallback si el campo se llama text/select de otra forma
    if not item_sprint:
        item_sprint = fields.get("Sprint") or fields.get("Iteration")

    # Filtrar por sprint si se especificó un parámetro objetivo
    if target_sprint and item_sprint:
        if item_sprint.strip().lower() != target_sprint.strip().lower():
            continue
    elif target_sprint and not item_sprint:
        # Si se busca un sprint pero el issue no tiene ninguno asignado, se omite
        continue

    matched_count += 1
    title = f"#{content['number']} {content['title']}".replace(":", "-")[:35]

    est_start = fields.get("Estimated Start Date")
    est_end = fields.get("Estimated End Date")
    act_start = fields.get("Start Date")
    act_end = fields.get("End Date")

    if est_start and est_end:
        mermaid_planned += f"    {title} (Plan) :p_{content['number']}, {est_start}, {est_end}\n"
    
    if act_start and act_end:
        mermaid_actual += f"    {title} (Actual) :active, a_{content['number']}, {act_start}, {act_end}\n"

# Ensamblar el contenido Markdown con el gráfico de Gantt
sprint_suffix = f" (Sprint: {target_sprint})" if target_sprint else ""
mermaid_content = f"""```mermaid
gantt
    title Project Schedule Comparison{sprint_suffix}
    dateFormat  YYYY-MM-DD

    section Estimated (Baseline)
{mermaid_planned if mermaid_planned else "    No estimated dates found : 2026-10-06, 2026-10-26"}

    section Actual Execution
{mermaid_actual if mermaid_actual else "    No actual dates found : 2026-10-06, 2026-10-26"}
```"""

os.makedirs("docs", exist_ok=True)
with open("docs/gantt-chart.md", "w") as f:
    f.write(f"# Project Gantt Comparison{sprint_suffix}\n\n" + mermaid_content)

print(f"Gantt comparison chart successfully generated! ({matched_count} issues processed)")
