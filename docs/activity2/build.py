"""Build the COMP 3111 Activity 2 PDF from the HTML sources in this folder.

Run: python docs/activity2/build.py
Needs Google Chrome (headless print-to-pdf) and PyMuPDF.
"""
import pathlib
import shutil
import subprocess
import sys
import tempfile

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent.parent
CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
OUT = ROOT / "comp3111 activity2 submission.pdf"

# ---------------------------------------------------------------- gantt data
# Day 0 = Monday 2 November 2026. Sprint 2 ran 23-26 November.
WEEKS = [("Nov 2-8", 0), ("Nov 9-15", 7), ("Nov 16-22", 14), ("Nov 23-29", 21)]
DAYS = ["M", "T", "W", "T", "F", "S", "S"]
DAY_COLOURS = ["#38c6e8", "#4f95d1", "#2f6fae", "#7b6ad0"]

PHASES = [
    ("Task 1  Requirements and design", [
        ("Project setup, Maven and JavaFX skeleton", 0, 4, "#5fd3ef"),
        ("SRS drafting: use cases and class diagram", 0, 11, "#4ac9e8"),
        ("Use-case specifications UC-1.1 to UC-3.8", 7, 18, "#35bde4"),
        ("SRS review against the brief", 18, 23, "#22add8"),
    ]),
    ("Task 2  Sprint 1: domain model", [
        ("Core models, enums, Money and Duration", 17, 20, "#5aa9d6"),
        ("Deal lifecycle and concurrency rules", 19, 21, "#4799cc"),
        ("SQLite persistence and schema", 21, 22, "#3588c1"),
        ("Role checks and password security", 21, 21, "#2377b6"),
    ]),
    ("Task 3  Sprint 2: the three portals", [
        ("Buyer portal, 10 screens", 21, 22, "#3f8ccc"),
        ("Seller portal, 10 screens", 21, 22, "#2f74b8"),
        ("Admin console, 8 screens", 21, 22, "#1f5f9e"),
    ]),
    ("Task 4  Verification and delivery", [
        ("Unit tests and coverage report", 22, 24, "#8a7fe0"),
        ("JavaDoc, screenshots and README", 22, 24, "#7b6ad0"),
        ("SRS gap fixes, UC-1.x to UC-3.x", 23, 24, "#6b5bd2"),
        ("Demo rehearsal and submission", 24, 25, "#5c4bc4"),
    ]),
]


def gantt_rows():
    out = []
    for name, activities in PHASES:
        out.append(
            f'<tr><td class="gphase">{name}</td>'
            + '<td class="gempty"></td>' * 28
            + "</tr>"
        )
        for label, start, end, colour in activities:
            cells, i = [], 0
            while i < 28:
                if start <= i <= end:
                    run = 0
                    while i + run < 28 and start <= i + run <= end:
                        run += 1
                    cells.append(
                        f'<td class="cell" colspan="{run}">'
                        f'<div class="bar" style="background:{colour}"></div></td>'
                    )
                    i += run
                else:
                    cells.append('<td class="gempty"></td>')
                    i += 1
            out.append(f'<tr><td class="glabel">{label}</td>' + "".join(cells) + "</tr>")
    return "\n".join(out)


def gantt_html():
    week_cells = "".join(
        f'<th class="week" colspan="7">{w}</th>' for w, _ in WEEKS
    )
    day_cells = "".join(
        f'<th class="day" style="background:{DAY_COLOURS[k // 7]}">{d}</th>'
        for k, d in enumerate(DAYS * 4)
    )
    legend = "".join(
        f'<span><span class="chip" style="background:{PHASES[i][1][0][3]}"></span>{PHASES[i][0]}</span>'
        for i in range(len(PHASES))
    )
    return f"""<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8">
<title>Project Status (Gantt Chart)</title>
<link rel="stylesheet" href="submission.css">
<style>@page {{ size: A4 landscape; margin: 0; }}</style>
</head><body>
<section class="page landscape">
  <div class="bar-title">Project Status (Gantt Chart)</div>
  <div class="bar-sub">HKUST Marketplace &middot; Group 1 &middot; 2 &ndash; 29 November 2026 &middot;
    Sprint 1: domain model and persistence &middot; Sprint 2: the three portals &middot;
    bars show the plan for each activity</div>
  <table class="gantt">
    <colgroup><col style="width:24.5%">{"".join('<col style="width:2.696%">' for _ in range(28))}</colgroup>
    <tr><th></th>{week_cells}</tr>
    <tr><th></th>{day_cells}</tr>
    {gantt_rows()}
  </table>
  <div class="legend">{legend}</div>
</section>
</body></html>"""


# ------------------------------------------------------------- sprint 2 data
# rows: (task, issue refs, estimate, hours completed on 23, 24, 25, 26 Nov)
AREAS = [
    ("Buyer portal &mdash; Chong Yui Kiu", [
        ("Browse marketplace: search, filter, sort", "#17", 6, [6, 0, 0, 0]),
        ("Listing detail with favourite and follow", "#18", 4, [4, 0, 0, 0]),
        ("Deal request: price offer, borrow request, claim", "#19", 6, [6, 0, 0, 0]),
        ("Buyer chat, My Deals, history, ratings, profile", "#20&ndash;#24", 14, [14, 0, 0, 0]),
    ]),
    ("Seller portal &mdash; Chan Lok Him", [
        ("Create listing with photos and type switching", "#25", 5, [5, 0, 0, 0]),
        ("My Listings, requests, chat, stats, reviews, profile", "#26&ndash;#31", 17, [0, 17, 0, 0]),
    ]),
    ("Admin console &mdash; Ho Sze Yui", [
        ("Manage users and soft-delete an account", "#32&ndash;#33", 6, [0, 6, 0, 0]),
        ("All listings, all deals, seller ratings", "#34&ndash;#36", 10, [0, 10, 0, 0]),
        ("Admin profile and notification board", "#37", 4, [0, 4, 0, 0]),
    ]),
    ("Verification and delivery", [
        ("Test suite, JaCoCo coverage, JavaDoc", "#38&ndash;#39", 7, [0, 7, 0, 0]),
        ("Screenshots, README, submission ZIP", "#40&ndash;#41", 5, [0, 5, 0, 0]),
        ("SRS gap fixes and UX polish", "PRs #87&ndash;#95", 12, [0, 0, 4, 8]),
        ("Manual SRS regression checklist", "#96", 3, [0, 0, 0, 0]),
    ]),
]
DATES = ["23 Nov", "24 Nov", "25 Nov", "26 Nov"]


def flat_rows():
    rows = []
    for area, tasks in AREAS:
        for task, ref, est, done in tasks:
            rows.append((task, ref, est, done))
    return rows


def backlog_table():
    rows = flat_rows()
    per_day = [sum(r[3][d] for r in rows) for d in range(4)]
    total_est = sum(r[2] for r in rows)
    total_done = sum(per_day)
    body = []
    for area, tasks in AREAS:
        body.append(f'<tr class="area"><td colspan="7">{area}</td></tr>')
        for task, ref, est, done in tasks:
            cells = "".join(
                f'<td class="num">{v if v else "&middot;"}</td>' for v in done
            )
            body.append(
                f'<tr><td>{task} <span class="issue">{ref}</span></td>'
                f'<td class="num">{est}</td>{cells}'
                f'<td class="num">{sum(done)}</td></tr>'
            )
    body.append(
        '<tr class="total"><td>Total hours</td><td>%d</td>%s<td>%d</td></tr>'
        % (total_est, "".join(f"<td>{v}</td>" for v in per_day), total_done)
    )
    return "\n".join(body), total_est, total_done, per_day


def chart(total_est, total_done, per_day):
    scope_added = 12   # SRS gap-fix row scoped at the 24 November meeting
    base = total_est - scope_added
    remaining = [base]
    for i, v in enumerate(per_day):
        remaining.append(remaining[-1] - v)
        if i == 1:      # scope grew at the end of day 2
            remaining[-1] += scope_added
    ideal = [base * (1 - i / 4) for i in range(5)]

    x0, x1, y0, y1 = 52, 424, 465, 55          # plot box
    def px(i):
        return x0 + i * (x1 - x0) / 4
    def py(v):
        return y0 - v / 100 * (y0 - y1)

    grid = "".join(
        f'<line x1="{x0}" y1="{py(v):.1f}" x2="{x1}" y2="{py(v):.1f}" stroke="#dde3ea" stroke-width="1"/>'
        f'<text x="{x0 - 7}" y="{py(v) + 3:.1f}" text-anchor="end" font-size="9" fill="#666">{v}</text>'
        for v in (0, 25, 50, 75, 100)
    )
    ideal_pts = " ".join(f"{px(i):.1f},{py(v):.1f}" for i, v in enumerate(ideal))
    actual_pts = " ".join(f"{px(i):.1f},{py(v):.1f}" for i, v in enumerate(remaining))
    markers = "".join(
        f'<circle cx="{px(i):.1f}" cy="{py(v):.1f}" r="4" fill="#fff" stroke="#1f5f9e" stroke-width="2.4"/>'
        for i, v in enumerate(remaining)
    )
    labels = "".join(
        f'<text x="{px(i):.1f}" y="{py(v) - 10:.1f}" text-anchor="middle" font-size="9.5" '
        f'font-weight="700" fill="#1f5f9e">{v:g}</text>'
        for i, v in enumerate(remaining)
    )
    ticks = "".join(
        f'<text x="{px(i):.1f}" y="{y0 + 16}" text-anchor="middle" font-size="8.5" fill="#444">'
        f'<tspan x="{px(i):.1f}" dy="0">{lab[0]}</tspan>'
        f'<tspan x="{px(i):.1f}" dy="11" font-size="7.5" fill="#777">{lab[1]}</tspan></text>'
        for i, lab in enumerate(
            [("start", ""), ("day 1", "23 Nov"), ("day 2", "24 Nov"), ("day 3", "25 Nov"), ("day 4", "26 Nov")]
        )
    )
    axis = (
        f'<line x1="{x0}" y1="{y0}" x2="{x1}" y2="{y0}" stroke="#9aa7b8" stroke-width="1"/>'
        f'<line x1="{x0}" y1="{y0}" x2="{x0}" y2="{y1}" stroke="#9aa7b8" stroke-width="1"/>'
    )
    return f"""<svg viewBox="0 0 440 520" width="100%" role="img" aria-label="Sprint 2 burndown chart">
  <text x="{x0}" y="20" font-size="9" fill="#555">Hours remaining</text>
  {grid}
  {axis}
  <polyline points="{ideal_pts}" fill="none" stroke="#9aa7b8" stroke-width="2" stroke-dasharray="6 4"/>
  <polyline points="{actual_pts}" fill="none" stroke="#1f5f9e" stroke-width="3"/>
  {markers}
  {labels}
  {ticks}
  <g font-size="8.5">
    <line x1="268" y1="17" x2="294" y2="17" stroke="#9aa7b8" stroke-width="2" stroke-dasharray="6 4"/>
    <text x="299" y="20" fill="#444">Ideal</text>
    <line x1="344" y1="17" x2="370" y2="17" stroke="#1f5f9e" stroke-width="3"/>
    <text x="375" y="20" fill="#444">Actual</text>
  </g>
</svg>"""


def sprint_html():
    table, total_est, total_done, per_day = backlog_table()
    left = (total_est - sum(per_day[:1]))
    return f"""<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8">
<title>Sprint 2 Backlog and Burndown Chart</title>
<link rel="stylesheet" href="submission.css">
<style>@page {{ size: A4 landscape; margin: 0; }}</style>
</head><body>
<section class="page landscape">
  <div class="bar-title">Sprint 2 Backlog and Burndown Chart</div>
  <div class="bar-sub">Sprint 2: 23 &ndash; 26 November 2026 &middot; estimates in hours &middot; source: GitHub issues, pull
    requests and <span style="font-family:Consolas,monospace">git log</span> on
    <span style="font-family:Consolas,monospace">main</span></div>

  <div class="two-col">
    <div class="col-left">
      <table class="backlog">
        <colgroup>
          <col style="width:47%"><col style="width:8%"><col style="width:9%">
          <col style="width:9%"><col style="width:9%"><col style="width:9%"><col style="width:9%">
        </colgroup>
        <tr>
          <th class="task">Sprint task</th>
          <th>Est. h</th>
          <th>23 Nov</th><th>24 Nov</th><th>25 Nov</th><th>26 Nov</th>
          <th>Closed h</th>
        </tr>
        {table}
      </table>
      <p class="note">Each figure is the estimated effort of the issues closed that day, not hours logged; the
        minutes record those. "SRS gap fixes" was scoped on 24 November (+12 h); checklist #96 was still open
        at sprint close, so 3 h remain. Days follow GitHub's UTC timestamps, so work after midnight HKT counts
        on the previous day.</p>
    </div>

    <div class="col-right">
      <div class="chart-card">
        <h3>Sprint 2 Burndown</h3>
        <div class="hint">Remaining estimated hours, measured at the end of each sprint day.</div>
        {chart(total_est, total_done, per_day)}
        <div class="note">
          Estimate {total_est} h: {total_est - 12} h planned plus 12 h of SRS gap fixes added on
          24 November (the step at day 2). Completed {total_done} h, {total_est - total_done} h remaining.
          The curve is below the ideal line from day 1 because the three portals were built in parallel.
        </div>
      </div>
    </div>
  </div>
</section>
</body></html>"""


# ------------------------------------------------------------------- render
def render(name, landscape=False):
    html = HERE / name
    pdf = pathlib.Path(tempfile.mkdtemp(prefix="a2-")) / (name.replace(".html", ".pdf"))
    subprocess.run(
        [
            CHROME,
            "--headless=new",
            "--disable-gpu",
            "--no-pdf-header-footer",
            "--run-all-compositor-stages-before-draw",
            "--virtual-time-budget=3000",
            f"--print-to-pdf={pdf}",
            html.as_uri(),
        ],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    return pdf


def main():
    (HERE / "gantt.html").write_text(gantt_html(), encoding="utf-8")
    (HERE / "sprint2.html").write_text(sprint_html(), encoding="utf-8")

    order = ["cover.html", "minutes.html", "gantt.html", "sprint2.html", "gitlog.html"]
    pdfs = [render(n) for n in order]

    import pymupdf
    doc = pymupdf.open()
    for p in pdfs:
        with pymupdf.open(p) as part:
            doc.insert_pdf(part)
    doc.set_metadata(
        {
            "title": "COMP 3111 Activity 2 Submission - HKUST Marketplace (Group 1)",
            "author": "Chong Yui Kiu, Chan Lok Him, Ho Sze Yui",
            "subject": "Meeting minutes, Gantt chart, Sprint 2 backlog and burndown, git commit log",
        }
    )
    doc.save(OUT, garbage=4, deflate=True)
    doc.close()
    print(f"{OUT}  ({OUT.stat().st_size // 1024} KB, {len(pdfs)} parts)")
    for p in pdfs:
        shutil.rmtree(p.parent, ignore_errors=True)


if __name__ == "__main__":
    main()
