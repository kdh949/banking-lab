from __future__ import annotations

import json
from pathlib import Path
from typing import Any


def load_scoring_artifact(path: str | Path) -> dict[str, Any]:
    artifact_path = Path(path)
    return json.loads(artifact_path.read_text(encoding="utf8"))


def top_risk_transactions(path: str | Path, limit: int = 5) -> list[dict[str, Any]]:
    artifact = load_scoring_artifact(path)
    results = artifact.get("results", [])
    if not isinstance(results, list):
        return []
    return sorted(
        [item for item in results if isinstance(item, dict)],
        key=lambda item: int(item.get("totalScore", 0)),
        reverse=True,
    )[:limit]
