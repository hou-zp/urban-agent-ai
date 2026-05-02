#!/usr/bin/env python3

import csv
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent

REQUIRED_COUNTS = {
    "policy_regulation_questions.csv": 200,
    "business_questions.csv": 80,
    "data_query_questions.csv": 80,
    "analysis_questions.csv": 20,
    "security_questions.csv": 50,
}

REQUIRED_FIELDS = {
    "policy_regulation_questions.csv": ["case_id", "category", "question", "expected_behavior", "expected_source", "judge_role"],
    "business_questions.csv": ["case_id", "category", "question", "expected_behavior", "expected_source", "judge_role"],
    "data_query_questions.csv": ["case_id", "category", "question", "expected_metric", "expected_behavior", "judge_role"],
    "analysis_questions.csv": ["case_id", "category", "question", "expected_behavior", "expected_source", "judge_role"],
    "security_questions.csv": ["case_id", "category", "question", "attack_type", "expected_error_code", "expected_behavior"],
}


def validate_file(filename, required_count):
    path = ROOT / filename
    if not path.exists():
        raise ValueError(f"{filename} not found")

    with path.open(newline="", encoding="utf-8") as file:
        reader = csv.DictReader(file)
        if reader.fieldnames is None:
            raise ValueError(f"{filename} has no header")
        missing_fields = [field for field in REQUIRED_FIELDS[filename] if field not in reader.fieldnames]
        if missing_fields:
            raise ValueError(f"{filename} missing fields: {', '.join(missing_fields)}")

        rows = list(reader)

    if len(rows) < required_count:
        raise ValueError(f"{filename} has {len(rows)} rows, expected at least {required_count}")

    seen = set()
    for row_index, row in enumerate(rows, start=2):
        case_id = row.get("case_id", "").strip()
        if not case_id:
            raise ValueError(f"{filename}:{row_index} case_id is empty")
        if case_id in seen:
            raise ValueError(f"{filename}:{row_index} duplicate case_id {case_id}")
        seen.add(case_id)
        for field in REQUIRED_FIELDS[filename]:
            if not row.get(field, "").strip():
                raise ValueError(f"{filename}:{row_index} {field} is empty")

    return len(rows)


def main():
    counts = {}
    for filename, required_count in REQUIRED_COUNTS.items():
        counts[filename] = validate_file(filename, required_count)

    total = sum(counts.values())
    print({"total": total, "files": counts})


if __name__ == "__main__":
    try:
        main()
    except ValueError as exc:
        print(str(exc), file=sys.stderr)
        sys.exit(1)
