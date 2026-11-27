"""Stage byte-identical, hash-pinned local dependencies for isolated AP development runs."""
from pathlib import Path
from zipfile import ZipFile
from io import BytesIO
import argparse
import hashlib
import json

parser = argparse.ArgumentParser()
parser.add_argument("--inventory", type=Path, required=True)
parser.add_argument("--mapping", type=Path, required=True)
parser.add_argument("--with-dragonrise", action="store_true")
args = parser.parse_args()
module = Path(__file__).resolve().parents[2]
out = module / "build/ap-runtime-inputs"
inventory = json.loads(args.inventory.read_text(encoding="utf-8-sig"))
out.mkdir(parents=True, exist_ok=True)
artifacts = []


def sha(data):
    return hashlib.sha256(data).hexdigest()


def stage(name, data, expected, origin, remap):
    if sha(data) != expected:
        raise ValueError("Exact dependency hash mismatch: " + origin)
    if Path(name).name != name or not name.endswith(".jar"):
        raise ValueError("Invalid staged dependency name")
    target = out / name
    if target.exists() and sha(target.read_bytes()) != expected:
        raise ValueError("Existing staged input differs; preserve it for investigation: " + str(target))
    if not target.exists():
        target.write_bytes(data)
    artifacts.append(dict(file=name, sha256=expected, origin=origin, remap=remap))


for index, item in enumerate(inventory["inputs"]):
    source = Path(item["path"])
    source_name = source.name.lower()
    if "dragonrise" in source_name:
        if not args.with_dragonrise:
            continue
        name = "dragonrise-reforge-exact.jar"
    elif "superbwarfare" in source_name:
        name = "superbwarfare-exact.jar"
    elif "kotlinforforge" in source_name:
        name = "kotlinforforge-exact.jar"
    else:
        raise ValueError("Unrecognized exact runtime dependency: " + str(source))
    data = source.read_bytes()
    stage(name, data, item["sha256"], str(source), name != "kotlinforforge-exact.jar")
    if name != "superbwarfare-exact.jar":
        continue
    with ZipFile(BytesIO(data)) as jar:
        for child in item["jarjar"]:
            entry = child["declaration"]["path"]
            nested = child["resolved"]
            stage(Path(entry).name, jar.read(entry), nested["sha256"], str(source) + "!/" + entry,
                  bool(nested.get("mods")))

mapping = args.mapping.resolve()
if not mapping.read_text(encoding="utf-8").startswith("tsrg2 left right"):
    raise ValueError("Expected exact SRG to official tsrg2 mapping")
result = dict(schemaVersion=1, artifacts=artifacts,
              mapping=dict(path=str(mapping), sha256=sha(mapping.read_bytes())),
              policy="EXACT_DEPENDENCY_COPIES_ONLY_NO_DNV_RELEASE_JAR")
(out / "manifest.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps(dict(status="PASS", artifacts=len(artifacts), mapping=result["mapping"])))
