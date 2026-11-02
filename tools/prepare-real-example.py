"""Reproduce the real-data teaching example; never changes the original CSVs."""
import csv
import datetime as dt
import hashlib
import json
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[1]
SOURCE = PROJECT / "data/originais/horario-com-salas.csv"
DESTINATION = PROJECT / "data/exemplo-real/horario.csv"
GROUP = "MEA1"


def prepare():
    with SOURCE.open(encoding="utf-8-sig", newline="") as stream:
        if stream.readline().strip() != "sep=;":
            stream.seek(0)
        reader = csv.DictReader(stream, delimiter=";")
        headers = reader.fieldnames
        rows = list(reader)
    selected = []
    mappings = []
    for original_row, row in enumerate(rows, 2):
        if GROUP not in {group.strip() for group in row["Turma"].split(",")}:
            continue
        if not row["Dia"]:
            raise ValueError("Target group has an undated occurrence: scope cannot be established")
        dt.datetime.strptime(row["Dia"], "%d/%m/%Y")
        selected.append(row)
        mappings.append({"exampleCsvRow": len(selected) + 1, "originalCsvRow": original_row})
    if not selected:
        raise ValueError("Empty selection")
    groups = sorted({g.strip() for row in selected for g in row["Turma"].split(",") if g.strip()})
    dates = [dt.datetime.strptime(row["Dia"], "%d/%m/%Y").date() for row in selected]
    start = min(dates) - dt.timedelta(days=min(dates).weekday())
    end = max(dates) + dt.timedelta(days=6-max(dates).weekday())
    DESTINATION.parent.mkdir(parents=True, exist_ok=True)
    with DESTINATION.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=headers, delimiter=";")
        writer.writeheader()
        writer.writerows(selected)
    manifest = {
        "scope": "REAL_SUBSET_NOT_FULL_INSTANCE",
        "source": "data/originais/horario-com-salas.csv",
        "sourceSha256": hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
        "subsetSha256": hashlib.sha256(DESTINATION.read_bytes()).hexdigest(),
        "selection": {"group": GROUP, "horizonStart": str(start), "horizonEnd": str(end), "rule": "All source rows containing group MEA1"},
        "sourceOccurrences": len(rows), "selectedOccurrences": len(selected),
        "outsideScopeOccurrences": len(rows) - len(selected),
        "allSelectedRowsUnchanged": True,
        "roomCatalog": "All 131 original rooms; original normal capacities; approved policy only",
        "externalRoomOccupancy": "Not modelled: this is an isolated subproblem, not a reservation in the full timetable",
        "experimentalAssumptions": {
            "profile": "Tarde (13:00-18:00), chosen for this experiment, not an official preference",
            "calendar": "Monday-Saturday 08:00-23:00; no additional closures in this experimental horizon",
            "classIdentity": "Literal CSV group tokens MEA1 and MEPB1; shared lessons remain single occurrences; external lessons are outside scope",
            "multipleProfiles": "Separate periods; only one profile used here"
        },
        "rows": mappings
    }
    DESTINATION.with_suffix(".manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    config = json.loads((PROJECT / "config/real.json").read_text(encoding="utf-8"))
    config.update(scenario="estudo-real-MEA1-nativo", synthetic=False,
                  scheduleFile="../data/exemplo-real/horario.csv",
                  horizonStart=str(start), horizonEnd=str(end),
                  unavailableDatesReviewed=True, classIdentifiersReviewed=True,
                  multipleProfilesMode="SEPARATE_PERIODS", profilesByClass={group: ["Tarde"] for group in groups},
                  problemSizes=[20, 50, len(selected)], independentRuns=3, numberOfCores=1)
    (PROJECT / "config/real-example.json").write_text(
        json.dumps(config, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Real subset: {len(selected)} unchanged occurrences; {len(rows)} in full source")


if __name__ == "__main__":
    prepare()
