#!/usr/bin/env python3
"""Build the AiCanCode CMS export (cms/snaplink.json) from docs/phase-*.md.

Usage:
    python3 scripts/build-cms.py            # write cms/snaplink.json
    python3 scripts/build-cms.py --check    # exit 1 if cms/snaplink.json is out of date

Standard library only. The schema is described in cms/README.md. The docs are the single source of
truth: never edit cms/snaplink.json by hand.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
OUT = ROOT / "cms" / "snaplink.json"
TRACKS = ["java", "python", "typescript"]
SECTIONS = [
    "Why it matters",
    "Objectives",
    "Step-by-step instructions",
    "AI-assist prompts",
    "Deliverables",
    "Self-check quiz",
    "What you learned",
    "Catch-up git commands",
]
FREE_PHASES = {0}  # phase 0 is a free preview; the rest is Pro / Elite / Campus

PROJECT = {
    "slug": "snaplink-url-shortener",
    "title": "SnapLink: URL Shortener with Analytics and AI Insights",
    "tagline": "Build a production-style URL shortener from requirements to monitoring, with AI as your pair programmer",
    "description": (
        "Over nine phases you build SnapLink like a professional team: requirements, design, a test-first "
        "implementation, CI/CD with GitHub Actions, Docker, optional free-tier deployment, observability and a "
        "retrospective. Pick Java, Python or TypeScript; the spec, database and tests are shared. Everything "
        "runs on your laptop, and the AI feature works offline."
    ),
    "difficulty": "beginner",
    "tech_stack": ["PostgreSQL", "Redis", "Docker", "GitHub Actions", "OpenAPI"],
    "tracks": [
        {"id": "java", "label": "Java", "tech_stack": ["Java 21", "Spring Boot", "JDBC", "JUnit 5", "Mockito", "JaCoCo"], "folder": "tracks/java"},
        {"id": "python", "label": "Python", "tech_stack": ["Python 3.11+", "FastAPI", "psycopg 3", "pytest"], "folder": "tracks/python"},
        {"id": "typescript", "label": "TypeScript", "tech_stack": ["Node.js 20+", "TypeScript", "Fastify", "pg", "Vitest"], "folder": "tracks/typescript"},
    ],
    "prerequisites": [
        {"title": "Git and the command line basics", "url": None},
        {"title": "One of: Java, Python or TypeScript fundamentals", "url": None},
    ],
    "outcomes": [
        "Turn a brief into testable requirements and user stories",
        "Design a REST API contract-first (OpenAPI) with a PostgreSQL schema and ADRs",
        "Implement test-first against a pending test suite, with a Redis cache that degrades gracefully",
        "Build CI/CD with GitHub Actions: matrix, caching, reusable workflows, service containers, GHCR",
        "Containerise with multi-stage Dockerfiles and run the stack with Docker Compose",
        "Add request IDs, JSON logs and metrics, run a game day and write a blameless post-mortem",
        "Use AI assistants deliberately and verify every output",
    ],
    "ai_feature": "AI insights for a link's traffic: offline template by default, optional Ollama or a free Gemini/Groq key",
    "requires_paid_services": False,
    "access": {"default": "pro", "plans": ["pro", "elite", "campus"], "free_phases": sorted(FREE_PHASES)},
    "repo": {
        "url": "https://github.com/AICanCode-org/snaplink",
        "starter_ref": "main",
        "solution_ref": "solution",
        "phase_tags": [f"phase-{n}-end" for n in range(9)],
        "releases": ["v1.0.0", "v1.1.0"],
    },
    "live_demo_url": None,
    "portfolio_blurb": (
        "Built SnapLink, a URL shortener with click analytics and AI insights: contract-first API, PostgreSQL + "
        "Redis with graceful degradation, ≥85 % test coverage, GitHub Actions CI/CD to GHCR, Docker, JSON logs, "
        "metrics and a game-day post-mortem."
    ),
}

TRACK_RE = re.compile(r"<!-- track:(\w+) -->\n?(.*?)<!-- /track -->\n?", re.S)


class DocError(Exception):
    pass


def front_matter(text: str, path: Path) -> tuple[dict, str]:
    if not text.startswith("---\n"):
        raise DocError(f"{path.name}: missing front matter")
    head, body = text[4:].split("\n---\n", 1)
    meta = {}
    for line in head.splitlines():
        key, _, value = line.partition(":")
        meta[key.strip()] = value.strip()
    return meta, body


def split_sections(body: str, path: Path) -> tuple[str, dict[str, str]]:
    title_match = re.search(r"^# (.+)$", body, re.M)
    if not title_match:
        raise DocError(f"{path.name}: missing '# Phase N · Title'")
    parts = re.split(r"^## (\d)\. (.+)$", body, flags=re.M)
    sections = {}
    for i in range(1, len(parts), 3):
        number, name, content = int(parts[i]), parts[i + 1].strip(), parts[i + 2].strip("\n")
        if number > len(SECTIONS) or SECTIONS[number - 1] != name:
            raise DocError(f"{path.name}: section {number} must be '{SECTIONS[number - 1] if number <= 8 else '?'}', got '{name}'")
        sections[name] = content
    missing = [s for s in SECTIONS if s not in sections]
    if missing:
        raise DocError(f"{path.name}: missing sections {missing}")
    return title_match.group(1).strip(), sections


def for_track(md: str, track: str) -> str:
    """Keep only `track`'s snippets (markers removed); drop the other tracks' snippets."""
    out = TRACK_RE.sub(lambda m: m.group(2) if m.group(1) == track else "", md)
    return re.sub(r"\n{3,}", "\n\n", out).strip()


def snippets(md: str, path: Path) -> list[dict]:
    found = []
    for m in TRACK_RE.finditer(md):
        if m.group(1) not in TRACKS:
            raise DocError(f"{path.name}: unknown track '{m.group(1)}'")
        found.append({"track": m.group(1), "content_md": m.group(2).strip()})
    return found


def bullets(md: str) -> list[str]:
    items, current = [], None
    for line in md.splitlines():
        m = re.match(r"^[-*] (?:\[[ x]\] )?(.*)$", line)
        if m:
            current = m.group(1).strip()
            items.append(current)
        elif line.startswith("  ") and items:
            items[-1] += " " + line.strip()
    return items


def subsections(md: str, prefix: str) -> tuple[str, list[tuple[str, str]]]:
    parts = re.split(rf"^### {prefix}(.*)$", md, flags=re.M)
    return parts[0].strip(), [(parts[i].strip(), parts[i + 1].strip()) for i in range(1, len(parts), 2)]


def with_tracks(md: str, path: Path) -> dict:
    item = {"content_md": md}
    snips = snippets(md, path)
    if snips:
        item["track_snippets"] = snips
        item["content_md_by_track"] = {t: for_track(md, t) for t in TRACKS}
    return item


def parse_steps(md: str, path: Path) -> tuple[dict, list[dict]]:
    intro, subs = subsections(md, r"Step ")
    if not subs:
        raise DocError(f"{path.name}: no '### Step N.x · Title' subsections")
    steps = []
    for order, (heading, content) in enumerate(subs, 1):
        number, _, title = heading.partition("·")
        steps.append({"order": order, "number": number.strip(), "title": title.strip(), **with_tracks(content, path)})
    return with_tracks(intro, path) if intro else {}, steps


def parse_prompts(md: str, path: Path) -> list[dict]:
    _, subs = subsections(md, r"Prompt ")
    prompts = []
    for order, (heading, content) in enumerate(subs, 1):
        title = heading.split(":", 1)[1].strip() if ":" in heading else heading
        block = re.search(r"```text\n(.*?)\n```", content, re.S)
        verify = re.search(r"\*\*Verify the output by:\*\*\s*(.*)", content, re.S)
        if not block or not verify:
            raise DocError(f"{path.name}: prompt '{heading}' needs a ```text block and '**Verify the output by:**'")
        prompts.append({
            "order": order,
            "title": title,
            "tool": "any",
            "prompt": block.group(1),
            "verify_by_md": " ".join(verify.group(1).split()),
        })
    if not prompts:
        raise DocError(f"{path.name}: no prompts")
    return prompts


def parse_quiz(md: str, path: Path) -> list[dict]:
    _, subs = subsections(md, r"Q")
    quiz = []
    for order, (heading, content) in enumerate(subs, 1):
        question = re.sub(r"^\d+\.\s*", "", heading)
        options, answers = [], []
        for line in content.splitlines():
            m = re.match(r"^- \[([ x])\] (.*)$", line)
            if m:
                if m.group(1) == "x":
                    answers.append(len(options))
                options.append(m.group(2).strip())
        explanation = " ".join(
            line[1:].strip() for line in content.splitlines() if line.startswith(">")
        ).replace("**Answer:**", "").strip()
        if len(options) < 2 or len(answers) != 1 or not explanation:
            raise DocError(f"{path.name}: Q{order} needs ≥2 options, exactly one [x] and a '> **Answer:**' line")
        quiz.append({
            "order": order,
            "type": "mcq",
            "q": question,
            "options": options,
            "answer": answers[0],
            "explanation": explanation,
        })
    if len(quiz) < 5:
        raise DocError(f"{path.name}: at least 5 quiz questions required, found {len(quiz)}")
    return quiz


def minutes(duration: str) -> int:
    m = re.match(r"([\d.]+)\s*h", duration)
    if not m:
        raise DocError(f"duration '{duration}' must look like '2.5 h'")
    return round(float(m.group(1)) * 60)


def parse_phase(path: Path) -> dict:
    meta, body = front_matter(path.read_text(encoding="utf-8"), path)
    title, sec = split_sections(body, path)
    number = int(meta["phase"])
    intro, steps = parse_steps(sec["Step-by-step instructions"], path)
    catch_up = re.search(r"```bash\n(.*?)\n```", sec["Catch-up git commands"], re.S)
    phase = {
        "number": number,
        "slug": meta["slug"],
        "title": meta["title"],
        "heading": title,
        "estimated_minutes": minutes(meta["duration"]),
        "access": "free" if number in FREE_PHASES else "pro",
        "git": {
            "start_ref": meta.get("start_tag") or meta.get("start_ref"),
            "end_tag": meta["end_tag"],
            "release": meta.get("release"),
        },
        "source_path": f"docs/{path.name}",
        "why_md": sec["Why it matters"],
        "objectives": bullets(sec["Objectives"]),
        "steps_intro": intro or None,
        "steps": steps,
        "ai_prompts": parse_prompts(sec["AI-assist prompts"], path),
        "deliverables": [{"text": t} for t in bullets(sec["Deliverables"])],
        "quiz": parse_quiz(sec["Self-check quiz"], path),
        "recap": bullets(sec["What you learned"]),
        "catch_up": {"md": sec["Catch-up git commands"], "commands": catch_up.group(1) if catch_up else None},
    }
    if not phase["objectives"] or not phase["deliverables"] or not phase["recap"]:
        raise DocError(f"{path.name}: objectives, deliverables and recap must be bullet lists")
    return phase


def legacy_import(phases: list[dict]) -> dict:
    """Fallback for the current CMS model (ProjectCreate: steps[title, content, order])."""
    steps = []
    for p in phases:
        path = DOCS / Path(p["source_path"]).name
        _, body = front_matter(path.read_text(encoding="utf-8"), path)
        steps.append({"order": p["number"] + 1, "title": p["heading"], "content": body.split("\n", 1)[1].strip()})
    return {
        "title": PROJECT["title"],
        "description": PROJECT["description"],
        "difficulty": PROJECT["difficulty"],
        "slug": PROJECT["slug"],
        "tech_stack": ", ".join(["Java", "Python", "TypeScript"] + PROJECT["tech_stack"]),
        "is_published": False,
        "steps": steps,
    }


def build() -> dict:
    paths = sorted(DOCS.glob("phase-*.md"), key=lambda p: int(re.search(r"phase-(\d+)", p.name).group(1)))
    phases = [parse_phase(p) for p in paths]
    numbers = [p["number"] for p in phases]
    if numbers != list(range(len(phases))):
        raise DocError(f"phases must be numbered 0..N without gaps, got {numbers}")
    project = dict(PROJECT, estimated_hours=round(sum(p["estimated_minutes"] for p in phases) / 60, 1))
    return {
        "schema_version": "2.0",
        "generator": "scripts/build-cms.py",
        "project": project,
        "phases": phases,
        "stats": {
            "phases": len(phases),
            "steps": sum(len(p["steps"]) for p in phases),
            "prompts": sum(len(p["ai_prompts"]) for p in phases),
            "quiz_questions": sum(len(p["quiz"]) for p in phases),
            "track_snippets": sum(len(s.get("track_snippets", [])) for p in phases for s in p["steps"]),
        },
        "legacy_import": legacy_import(phases),
    }


def main() -> int:
    try:
        data = build()
    except DocError as exc:
        print(f"build-cms: {exc}", file=sys.stderr)
        return 2
    text = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
    if "--check" in sys.argv:
        if not OUT.exists() or OUT.read_text(encoding="utf-8") != text:
            print("cms/snaplink.json is out of date: run python3 scripts/build-cms.py", file=sys.stderr)
            return 1
        print("cms/snaplink.json is up to date")
        return 0
    OUT.parent.mkdir(exist_ok=True)
    OUT.write_text(text, encoding="utf-8")
    print(f"wrote {OUT.relative_to(ROOT)}: {json.dumps(data['stats'])}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
