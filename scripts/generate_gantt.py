#!/usr/bin/env python3
"""Generate Gantt comparison charts (Planned vs Actual) from GitHub Projects v2.

Fetches tasks from the HoDoWo organization project (or repository project),
extracts estimated baseline dates and actual execution dates, and renders
Mermaid Gantt charts grouped by sprint.
"""

import argparse
import datetime as dt
import json
import os
import re
import sys
from collections import defaultdict
import requests

# Reconfigure stdout to UTF-8 for Windows consoles
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")


def parse_args():
    parser = argparse.ArgumentParser(description="Generate Gantt comparison charts.")
    parser.add_argument("--token", default=os.getenv("GH_TOKEN") or os.getenv("GITHUB_TOKEN"),
                        help="GitHub Personal Access Token (defaults to GH_TOKEN or GITHUB_TOKEN)")
    parser.add_argument("--owner", default=os.getenv("OWNER", "HoDoWo"),
                        help="GitHub organization or repository owner (default: HoDoWo)")
    parser.add_argument("--project-number", type=int,
                        default=int(os.getenv("PROJECT_NUMBER", "1")),
                        help="GitHub Project v2 number (default: 1)")
    parser.add_argument("--repository", default=os.getenv("REPOSITORY", "HoDoWo/app-kotlin"),
                        help="Full repository name (default: HoDoWo/app-kotlin)")
    parser.add_argument("--sprint", default=os.getenv("TARGET_SPRINT", "all"),
                        help="Target sprint name or 'all' (default: all)")
    parser.add_argument("--out", default="docs/gantt-chart.md",
                        help="Output markdown file path (default: docs/gantt-chart.md)")
    return parser.parse_args()


ORG_PROJECT_QUERY = """
query($owner: String!, $num: Int!, $cursor: String) {
  organization(login: $owner) {
    projectV2(number: $num) {
      title
      items(first: 100, after: $cursor) {
        pageInfo {
          hasNextPage
          endCursor
        }
        nodes {
          id
          content {
            ... on Issue {
              number
              title
              repository { nameWithOwner }
            }
            ... on PullRequest {
              number
              title
              repository { nameWithOwner }
            }
          }
          fieldValues(first: 30) {
            nodes {
              __typename
              ... on ProjectV2ItemFieldDateValue {
                date
                field { ... on ProjectV2FieldCommon { name } }
              }
              ... on ProjectV2ItemFieldSingleSelectValue {
                name
                field { ... on ProjectV2FieldCommon { name } }
              }
              ... on ProjectV2ItemFieldIterationValue {
                title
                startDate
                duration
                field { ... on ProjectV2FieldCommon { name } }
              }
            }
          }
        }
      }
    }
  }
}
"""

REPO_PROJECT_QUERY = """
query($owner: String!, $repo: String!, $cursor: String) {
  repository(owner: $owner, name: $repo) {
    projectsV2(first: 5) {
      nodes {
        title
        number
        items(first: 100, after: $cursor) {
          pageInfo {
            hasNextPage
            endCursor
          }
          nodes {
            id
            content {
              ... on Issue {
                number
                title
                repository { nameWithOwner }
              }
              ... on PullRequest {
                number
                title
                repository { nameWithOwner }
              }
            }
            fieldValues(first: 30) {
              nodes {
                __typename
                ... on ProjectV2ItemFieldDateValue {
                  date
                  field { ... on ProjectV2FieldCommon { name } }
                }
                ... on ProjectV2ItemFieldSingleSelectValue {
                  name
                  field { ... on ProjectV2FieldCommon { name } }
                }
                ... on ProjectV2ItemFieldIterationValue {
                  title
                  startDate
                  duration
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


def fetch_items(token, owner, project_number, repository):
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json",
    }
    
    # Try fetching from organization first
    items = []
    cursor = None
    project_title = f"Project #{project_number}"
    try:
        while True:
            resp = requests.post(
                "https://api.github.com/graphql",
                json={"query": ORG_PROJECT_QUERY, "variables": {"owner": owner, "num": project_number, "cursor": cursor}},
                headers=headers,
                timeout=60
            )
            data = resp.json()
            org = (data.get("data") or {}).get("organization")
            proj = (org or {}).get("projectV2")
            if not proj:
                break
            project_title = proj.get("title", project_title)
            page = proj["items"]
            items.extend(page["nodes"])
            if not page["pageInfo"]["hasNextPage"]:
                return project_title, items
            cursor = page["pageInfo"]["endCursor"]
    except Exception:
        items = []

    if items:
        return project_title, items

    # Fallback to repository projects
    repo_owner, repo_name = repository.split("/") if "/" in repository else (owner, repository)
    cursor = None
    while True:
        resp = requests.post(
            "https://api.github.com/graphql",
            json={"query": REPO_PROJECT_QUERY, "variables": {"owner": repo_owner, "repo": repo_name, "cursor": cursor}},
            headers=headers,
            timeout=60
        )
        data = resp.json()
        repo_data = (data.get("data") or {}).get("repository")
        projects = (repo_data or {}).get("projectsV2", {}).get("nodes", [])
        if not projects:
            raise SystemExit("No GitHub Projects v2 visible for this repository or organization: "
                            "check token permissions (read:project) and project number.")
        proj = projects[0]
        project_title = proj.get("title", project_title)
        page = proj["items"]
        items.extend(page["nodes"])
        if not page["pageInfo"]["hasNextPage"]:
            break
        cursor = page["pageInfo"]["endCursor"]

    return project_title, items


def sanitize_title(title):
    cleaned = re.sub(r'[:"\'\n\r;`#]', ' ', title)
    return re.sub(r'\s+', ' ', cleaned).strip()


def normalize_date_range(start_str, end_str):
    if not start_str or not end_str:
        return None, None
    try:
        sd = dt.date.fromisoformat(start_str)
        ed = dt.date.fromisoformat(end_str)
        # Ensure end date is strictly after start date for Mermaid gantt rendering
        if ed <= sd:
            ed = sd + dt.timedelta(days=1)
        return sd.isoformat(), ed.isoformat()
    except ValueError:
        return start_str, end_str


def build_sprint_gantt(sprint_name, planned, actual):
    lines = [
        "```mermaid",
        "gantt",
        f"    title {sprint_name} Schedule: Planned vs Actual",
        "    dateFormat  YYYY-MM-DD",
        "    axisFormat  %d/%m",
        ""
    ]
    
    lines.append("    section Planned (Baseline)")
    if planned:
        for t in planned:
            lines.append(f"    {t['title']} (Plan) :{t['id']}, {t['start']}, {t['end']}")
    else:
        lines.append("    No planned dates found : 2026-10-06, 2026-10-27")
        
    lines.append("")
    lines.append("    section Actual Execution")
    if actual:
        for t in actual:
            tag = "done, " if t['status'] == "Done" else "active, "
            lines.append(f"    {t['title']} (Actual) :{tag}{t['id']}, {t['start']}, {t['end']}")
    else:
        lines.append("    No actual dates found : 2026-10-06, 2026-10-27")
        
    lines.append("```")
    return "\n".join(lines)


def build_combined_gantt(sprint_dict):
    lines = [
        "```mermaid",
        "gantt",
        "    title Project Schedule Comparison: Planned vs Actual",
        "    dateFormat  YYYY-MM-DD",
        "    axisFormat  %b %d",
        ""
    ]
    
    def sprint_sort_key(s):
        match = re.search(r'\d+', s)
        return (0, int(match.group())) if match else (1, s)
        
    ordered_sprints = sorted(sprint_dict.keys(), key=sprint_sort_key)
    
    for s in ordered_sprints:
        p_list = sprint_dict[s]["planned"]
        a_list = sprint_dict[s]["actual"]
        if not p_list and not a_list:
            continue
            
        if p_list:
            lines.append(f"    section {s} - Planned")
            for t in p_list:
                lines.append(f"    {t['title']} (Plan) :{t['id']}, {t['start']}, {t['end']}")
                
        if a_list:
            lines.append(f"    section {s} - Actual")
            for t in a_list:
                tag = "done, " if t['status'] == "Done" else "active, "
                lines.append(f"    {t['title']} (Actual) :{tag}{t['id']}, {t['start']}, {t['end']}")
                
        lines.append("")
        
    lines.append("```")
    return "\n".join(lines)


def main():
    args = parse_args()
    
    if not args.token:
        # Check if local hosts.yml has a token
        hosts_path = os.path.expanduser("~/.config/gh/hosts.yml")
        if not os.path.exists(hosts_path):
            hosts_path = os.path.join(os.getenv("APPDATA", ""), "GitHub CLI", "hosts.yml")
        if os.path.exists(hosts_path):
            try:
                for line in open(hosts_path):
                    if "oauth_token:" in line:
                        args.token = line.split("oauth_token:")[1].strip()
                        break
            except Exception:
                pass

    if not args.token:
        raise SystemExit("Error: GH_TOKEN or GITHUB_TOKEN environment variable is required.")

    print(f"Fetching Project data (Owner: {args.owner}, Number: {args.project_number})...")
    project_title, items = fetch_items(args.token, args.owner, args.project_number, args.repository)
    print(f"Retrieved {len(items)} items from '{project_title}'.")

    sprint_tasks = defaultdict(lambda: {"planned": [], "actual": []})

    for item in items:
        content = item.get("content")
        if not content or "title" not in content:
            continue
            
        repo_name = (content.get("repository") or {}).get("nameWithOwner", "")
        prefix = "Backend" if "Backend" in repo_name else "App"
        num = content.get("number", "")
        clean_name = sanitize_title(content["title"])
        short_title = f"{prefix}#{num} {clean_name}"[:35].strip()
        task_id_base = f"{prefix.lower()}_{num}"
        
        # Extract fields with stripped names and normalized lower-case keys
        fields = {}
        for fv in (item.get("fieldValues") or {}).get("nodes", []):
            if not fv or not fv.get("field"):
                continue
            name = (fv["field"].get("name") or "").strip().lower()
            if not name:
                continue
            if "date" in fv:
                fields[name] = fv["date"]
            elif "title" in fv:
                fields[name] = fv["title"]
            elif "name" in fv:
                fields[name] = fv["name"]
                
        sprint = fields.get("sprint", "Backlog")
        status = fields.get("status", "")
        
        # Resilient field name resolution (matching with or without leading spaces, case-insensitive)
        est_start = (fields.get("estimated start date") or
                     fields.get("estimated start") or
                     fields.get("planned start date") or
                     fields.get("planned start"))
        est_end = (fields.get("estimated end date") or
                   fields.get("estimated end") or
                   fields.get("planned end date") or
                   fields.get("planned end"))
        act_start = (fields.get("start date") or
                     fields.get("actual start date") or
                     fields.get("actual start"))
        act_end = (fields.get("end date") or
                   fields.get("actual end date") or
                   fields.get("actual end"))
        
        est_s, est_e = normalize_date_range(est_start, est_end)
        act_s, act_e = normalize_date_range(act_start, act_end)
        
        if est_s and est_e:
            sprint_tasks[sprint]["planned"].append({
                "title": short_title,
                "id": f"p_{task_id_base}",
                "start": est_s,
                "end": est_e,
                "status": status
            })
            
        if act_s and act_e:
            sprint_tasks[sprint]["actual"].append({
                "title": short_title,
                "id": f"a_{task_id_base}",
                "start": act_s,
                "end": act_e,
                "status": status
            })

    # Sort tasks chronologically for clean Gantt display
    for s in sprint_tasks:
        sprint_tasks[s]["planned"].sort(key=lambda x: (x["start"], x["end"], x["title"]))
        sprint_tasks[s]["actual"].sort(key=lambda x: (x["start"], x["end"], x["title"]))

    md_output = [
        "# Project Gantt Comparison",
        "",
        f"Automated schedule comparison generated from the **{args.owner}** GitHub Project.",
        "",
    ]

    if args.sprint != "all" and args.sprint in sprint_tasks:
        p = sprint_tasks[args.sprint]["planned"]
        a = sprint_tasks[args.sprint]["actual"]
        md_output.append(f"## {args.sprint} Schedule Comparison")
        md_output.append("")
        md_output.append(build_sprint_gantt(args.sprint, p, a))
    else:
        # Full view: Combined chart followed by per-sprint comparisons
        md_output.append("## Overall Schedule Comparison (By Sprint)")
        md_output.append("")
        md_output.append(build_combined_gantt(sprint_tasks))
        md_output.append("")
        
        def sprint_sort_key(s):
            match = re.search(r'\d+', s)
            return (0, int(match.group())) if match else (1, s)

        for s in sorted(sprint_tasks.keys(), key=sprint_sort_key):
            p = sprint_tasks[s]["planned"]
            a = sprint_tasks[s]["actual"]
            if p or a:
                md_output.append(f"## {s} Detail (Planned vs Actual)")
                md_output.append("")
                md_output.append(build_sprint_gantt(s, p, a))
                md_output.append("")

    out_dir = os.path.dirname(args.out)
    if out_dir:
        os.makedirs(out_dir, exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        f.write("\n".join(md_output) + "\n")

    print(f"Gantt comparison chart successfully saved to {args.out}")


if __name__ == "__main__":
    main()
