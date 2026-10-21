#!/usr/bin/env python3
"""Earned Value report for the HoDoWo GitHub Project.

Reads the items of the organization Project (fields Status, Time estimated,
Time spend and Sprint) and calculates the Earned Value indicators with the
formulas of the IT Project Management course:

    PV  = % planned   x BAC     (Sprint 0 baseline, linear inside each Sprint)
    EV  = % completed x BAC     (estimated hours of the items in Done / total hours)
    AC  = actual cost to date   (hours spent x hourly rate + hypothetical services)
    EAC = BAC / CPI    ETC = EAC - AC    VAC = BAC - EAC
    CPI = EV / AC      SPI = EV / PV

Output (in --out): README.md with the report, history.csv with one row per day
and ev.svg with PV, EV and AC over time. The report is also written to the
GitHub Actions run summary when GITHUB_STEP_SUMMARY is set.

Local test without GitHub:  python3 earned_value.py --items-json items.json --date 2026-10-21
"""
import argparse
import csv
import datetime as dt
import json
import os
import sys
import urllib.request
from xml.sax.saxutils import escape

QUERY = """
query($owner:String!, $num:Int!, $cursor:String) {
  organization(login:$owner) { projectV2(number:$num) {
    title
    items(first:100, after:$cursor) {
      pageInfo { hasNextPage endCursor }
      nodes {
        content {
          ... on Issue { number title }
          ... on PullRequest { number title }
          ... on DraftIssue { title }
        }
        fieldValues(first:30) { nodes {
          ... on ProjectV2ItemFieldNumberValue { number field { ... on ProjectV2FieldCommon { name } } }
          ... on ProjectV2ItemFieldTextValue { text field { ... on ProjectV2FieldCommon { name } } }
          ... on ProjectV2ItemFieldSingleSelectValue { name field { ... on ProjectV2FieldCommon { name } } }
          ... on ProjectV2ItemFieldIterationValue { title startDate duration field { ... on ProjectV2FieldCommon { name } } }
        } }
      }
    }
  } }
}
"""

HISTORY_COLUMNS = ["date", "pv", "ev", "ac", "bac", "eac", "etc", "vac", "cpi", "spi",
                   "hours_done_estimated", "hours_spent", "services"]


# ---------------------------------------------------------------- data access

def graphql(token, variables):
    body = json.dumps({"query": QUERY, "variables": variables}).encode()
    req = urllib.request.Request(
        "https://api.github.com/graphql", data=body,
        headers={"Authorization": "Bearer " + token, "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=60) as res:
        data = json.load(res)
    if data.get("errors"):
        raise RuntimeError(json.dumps(data["errors"], indent=1))
    return data["data"]


def fetch_items(token, owner, number):
    items, cursor = [], None
    while True:
        data = graphql(token, {"owner": owner, "num": number, "cursor": cursor})
        project = (data.get("organization") or {}).get("projectV2")
        if not project:
            raise RuntimeError("Project %s/%s not found or token without access" % (owner, number))
        page = project["items"]
        items += page["nodes"]
        if not page["pageInfo"]["hasNextPage"]:
            return items
        cursor = page["pageInfo"]["endCursor"]


def to_hours(value):
    if not value:
        return 0.0
    if value.get("number") is not None:
        return float(value["number"])
    try:
        return float(str(value.get("text") or "0").replace(",", "."))
    except ValueError:
        return 0.0


def normalise(node, fields, done_statuses):
    values = {}
    for v in (node.get("fieldValues") or {}).get("nodes") or []:
        if v and v.get("field"):
            values[v["field"]["name"]] = v
    content = node.get("content") or {}
    sprint = values.get(fields["sprint"]) or {}
    status = (values.get(fields["status"]) or {}).get("name") or ""
    start = None
    if sprint.get("startDate"):
        start = dt.date.fromisoformat(sprint["startDate"])
    return {
        "number": content.get("number"),
        "title": content.get("title", ""),
        "status": status,
        "done": status in done_statuses,
        "estimate": to_hours(values.get(fields["estimate"])),
        "spent": to_hours(values.get(fields["spent"])),
        "sprint": sprint.get("title"),
        "sprint_start": start,
        "sprint_end": start + dt.timedelta(days=sprint["duration"]) if start else None,
    }


# ---------------------------------------------------------------- calculation

def planned_hours(baseline, day, only=None):
    """Hours of the Sprint 0 baseline that should be done by the end of `day`."""
    total = 0.0
    for sprint in baseline:
        if only and sprint["name"] != only:
            continue
        start = dt.date.fromisoformat(sprint["start"])
        end = dt.date.fromisoformat(sprint["end"])
        length = (end - start).days + 1
        elapsed = min(max((day - start).days + 1, 0), length)
        total += sprint["hours"] * elapsed / length
    return total


def services_cost(services, day, since=None):
    return sum(s["amount"] for s in services
               if dt.date.fromisoformat(s["date"]) <= day
               and (since is None or dt.date.fromisoformat(s["date"]) >= since))


def ratio(a, b):
    return a / b if b else None


def indicators(pv, ev, ac, bac):
    cpi, spi = ratio(ev, ac), ratio(ev, pv)
    eac = bac / cpi if cpi else None
    return {
        "pv": pv, "ev": ev, "ac": ac, "bac": bac, "cpi": cpi, "spi": spi, "eac": eac,
        "etc": eac - ac if eac is not None else None,
        "vac": bac - eac if eac is not None else None,
    }


def current_sprint(items, baseline, day):
    """Name and window of the Sprint that contains `day` (Project iteration first, then baseline)."""
    iterations = {(i["sprint"], i["sprint_start"], i["sprint_end"]) for i in items if i["sprint_start"]}
    for name, start, end in sorted(iterations, key=lambda x: x[1]):
        if start <= day < end:
            return name, start, end - dt.timedelta(days=1)
    past = [x for x in iterations if x[1] <= day]
    if past:
        name, start, end = max(past, key=lambda x: x[1])
        return name, start, end - dt.timedelta(days=1)
    for sprint in baseline:
        start, end = dt.date.fromisoformat(sprint["start"]), dt.date.fromisoformat(sprint["end"])
        if start <= day <= end:
            return sprint["name"], start, end
    return None, None, None


def calculate(items, cfg, day):
    bac, rate, total = cfg["bac"], cfg["hourly_rate"], cfg["total_hours"]
    baseline, services = cfg["baseline"], cfg["services"]

    done_est = sum(i["estimate"] for i in items if i["done"])
    spent = sum(i["spent"] for i in items)
    services_to_date = services_cost(services, day)
    cumulative = indicators(
        pv=planned_hours(baseline, day) / total * bac,
        ev=done_est / total * bac,
        ac=spent * rate + services_to_date,
        bac=bac)
    cumulative.update(hours_done_estimated=done_est, hours_spent=spent, services=services_to_date,
                      hours_planned=planned_hours(baseline, day))

    name, start, end = current_sprint(items, baseline, day)
    sprint = None
    if name:
        in_sprint = [i for i in items if i["sprint"] == name]
        base_name = name if any(b["name"] == name for b in baseline) else None
        s_done = sum(i["estimate"] for i in in_sprint if i["done"])
        s_spent = sum(i["spent"] for i in in_sprint)
        s_planned = planned_hours(baseline, day, only=base_name) if base_name else 0.0
        s_services = services_cost(services, day, since=start)
        sprint = indicators(pv=s_planned / total * bac, ev=s_done / total * bac,
                            ac=s_spent * rate + s_services, bac=bac)
        sprint.update(name=name, start=start, end=end, items=len(in_sprint),
                      open=[i for i in in_sprint if not i["done"]],
                      hours_estimated=sum(i["estimate"] for i in in_sprint),
                      hours_done_estimated=s_done, hours_spent=s_spent, hours_planned=s_planned,
                      services=s_services)
    return cumulative, sprint


# ---------------------------------------------------------------- output

def eur(x):
    if x is None:
        return "–"
    s = "{:,.0f} €".format(abs(x))
    return ("−" + s) if x < 0 else s


def num(x):
    return "–" if x is None else "{:.2f}".format(x)


def reading(cum):
    notes = []
    if cum["cpi"] is not None:
        notes.append("CPI {}: {}".format(num(cum["cpi"]),
                     "under budget (the completed work cost less than planned)" if cum["cpi"] >= 1
                     else "over budget (the completed work cost more than planned)"))
    if cum["spi"] is not None:
        notes.append("SPI {}: {}".format(num(cum["spi"]),
                     "ahead of or on schedule" if cum["spi"] >= 1 else "behind schedule"))
    if cum["vac"] is not None:
        notes.append("VAC {}: {}".format(eur(cum["vac"]),
                     "saving money" if cum["vac"] >= 0 else "the project would end over the Sprint 0 budget"))
    return notes


def report(cum, sprint, cfg, day, generated):
    rows = [
        ("PV", "Planned Value", "% planned × BAC", "pv"),
        ("EV", "Earned Value", "% completed × BAC", "ev"),
        ("AC", "Actual Cost", "hours spent × rate + services", "ac"),
        ("BAC", "Budget at Completion", "Sprint 0 estimate", "bac"),
        ("EAC", "Estimate at Completion", "BAC / CPI", "eac"),
        ("ETC", "Estimate to Complete", "EAC − AC", "etc"),
        ("VAC", "Variance at Completion", "BAC − EAC", "vac"),
        ("CPI", "Cost Performance Index", "EV / AC", "cpi"),
        ("SPI", "Schedule Performance Index", "EV / PV", "spi"),
    ]
    head = sprint["name"] if sprint else "Sprint"
    out = ["# Earned Value – HoDoWo", "",
           "Status on **{}** (generated {} UTC).".format(day, generated), "",
           "| KPI | Name | Formula | {} | Cumulative |".format(head),
           "|---|---|---|---:|---:|"]
    for kpi, name, formula, key in rows:
        fmt = num if key in ("cpi", "spi") else eur
        s_val = "–"
        if sprint and key in ("pv", "ev", "ac", "cpi", "spi"):
            s_val = fmt(sprint[key])
        out.append("| **{}** | {} | {} | {} | {} |".format(kpi, name, formula, s_val, fmt(cum[key])))
    total = cfg["total_hours"]
    out += ["", "## Reading", ""] + ["- " + n for n in reading(cum)]
    out += ["", "## Inputs", "",
            "- BAC: {} (total cost estimated in Sprint 0), {} h planned for the whole project.".format(eur(cfg["bac"]), total),
            "- % planned: {:.1f} h of the Sprint 0 baseline by {} → {:.1%}.".format(cum["hours_planned"], day, cum["hours_planned"] / total),
            "- % completed: {:.1f} h estimated on the items in {} → {:.1%}.".format(
                cum["hours_done_estimated"], " / ".join(cfg["done_statuses"]), cum["hours_done_estimated"] / total),
            "- AC: {:.1f} h spent × {} €/h + {} of services.".format(cum["hours_spent"], cfg["hourly_rate"], eur(cum["services"])),
            "- " + cfg["services_note"]]
    if sprint:
        out += ["", "## {} ({} → {})".format(sprint["name"], sprint["start"], sprint["end"]), "",
                "- Items: {}, estimated {:.1f} h, completed {:.1f} h, spent {:.1f} h.".format(
                    sprint["items"], sprint["hours_estimated"], sprint["hours_done_estimated"], sprint["hours_spent"])]
        if sprint["open"]:
            out += ["- Open items:"]
            for i in sorted(sprint["open"], key=lambda x: (x["status"], x["number"] or 0)):
                out.append("  - #{} {} – {} ({:g} h estimated, {:g} h spent)".format(
                    i["number"] or "", i["title"], i["status"] or "no status", i["estimate"], i["spent"]))
    out += ["", "![PV, EV and AC over time](ev.svg)", ""]
    return "\n".join(out)


def update_history(path, cum, day):
    rows = []
    if os.path.exists(path):
        with open(path, newline="") as f:
            rows = [r for r in csv.DictReader(f) if r["date"] != str(day)]
    row = {k: ("" if cum.get(k) is None else round(cum[k], 4)) for k in HISTORY_COLUMNS if k != "date"}
    row["date"] = str(day)
    rows.append(row)
    rows.sort(key=lambda r: r["date"])
    with open(path, "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=HISTORY_COLUMNS)
        w.writeheader()
        w.writerows(rows)
    return rows


def chart(rows, cfg):
    pts = [(dt.date.fromisoformat(r["date"]), float(r["pv"] or 0), float(r["ev"] or 0), float(r["ac"] or 0)) for r in rows]
    first = dt.date.fromisoformat(cfg["baseline"][0]["start"])
    last = dt.date.fromisoformat(cfg["baseline"][-1]["end"])
    W, H, L, R, T, B = 900, 420, 70, 30, 60, 50
    ymax = max([p[k] for p in pts for k in (1, 2, 3)] + [1]) * 1.15
    span = max((last - first).days, 1)

    def X(d): return L + (d - first).days / span * (W - L - R)
    def Y(v): return T + (1 - v / ymax) * (H - T - B)

    o = ['<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" font-family="sans-serif" font-size="12">' % (W, H),
         '<rect width="100%" height="100%" fill="white"/>',
         '<text x="%d" y="26" font-size="17" font-weight="bold">Earned Value – cumulative</text>' % L]
    for i in range(6):
        v = ymax * i / 5
        o.append('<line x1="%d" y1="%.1f" x2="%d" y2="%.1f" stroke="#eee"/>' % (L, Y(v), W - R, Y(v)))
        o.append('<text x="%d" y="%.1f" text-anchor="end">%s</text>' % (L - 8, Y(v) + 4, escape(eur(v))))
    for sprint in cfg["baseline"]:
        x = X(dt.date.fromisoformat(sprint["start"]))
        o.append('<line x1="%.1f" y1="%d" x2="%.1f" y2="%d" stroke="#ddd" stroke-dasharray="3 3"/>' % (x, T, x, H - B))
        o.append('<text x="%.1f" y="%d" fill="#777">%s</text>' % (x + 4, H - B + 18, escape(sprint["name"])))
    colors = {1: ("#8250df", "PV"), 2: ("#2da44e", "EV"), 3: ("#cf222e", "AC")}
    for k, (color, label) in colors.items():
        line = " ".join("%.1f,%.1f" % (X(p[0]), Y(p[k])) for p in pts)
        o.append('<polyline points="%s" fill="none" stroke="%s" stroke-width="3"/>' % (line, color))
        for p in pts[-1:]:
            o.append('<circle cx="%.1f" cy="%.1f" r="4" fill="%s"/>' % (X(p[0]), Y(p[k]), color))
        o.append('<text x="%d" y="26" fill="%s" font-weight="bold">● %s</text>' % (W - R - 180 + 60 * (k - 1), color, label))
    o.append('</svg>')
    return "\n".join(o)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--config", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--date", help="status date (YYYY-MM-DD), default today in UTC")
    ap.add_argument("--items-json", help="read raw Project items from a file instead of GitHub")
    args = ap.parse_args()

    cfg = json.load(open(args.config))
    now = dt.datetime.now(dt.timezone.utc).replace(microsecond=0)
    day = dt.date.fromisoformat(args.date) if args.date else now.date()

    if args.items_json:
        raw = json.load(open(args.items_json))
    else:
        token = os.environ.get("GH_TOKEN")
        if not token:
            sys.exit("GH_TOKEN is not set (use the PROJECT_TOKEN secret)")
        raw = fetch_items(token, os.environ["OWNER"], int(os.environ["PROJECT_NUMBER"]))
    items = [normalise(n, cfg["fields"], cfg["done_statuses"]) for n in raw]
    if not items:
        sys.exit("The Project returned no items")

    cum, sprint = calculate(items, cfg, day)
    os.makedirs(args.out, exist_ok=True)
    rows = update_history(os.path.join(args.out, "history.csv"), cum, day)
    with open(os.path.join(args.out, "ev.svg"), "w") as f:
        f.write(chart(rows, cfg))
    text = report(cum, sprint, cfg, day, now.strftime("%Y-%m-%d %H:%M"))
    with open(os.path.join(args.out, "README.md"), "w") as f:
        f.write(text)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a") as f:
            f.write(text.replace("![PV, EV and AC over time](ev.svg)", ""))
    print(text)


if __name__ == "__main__":
    main()
