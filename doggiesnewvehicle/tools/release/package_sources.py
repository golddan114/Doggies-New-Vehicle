"""Export public DNV code and build inputs from one committed snapshot."""

import argparse
from hashlib import sha256
from io import BytesIO
import json
from pathlib import Path, PurePosixPath
import subprocess
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo


ROOT = Path(__file__).resolve().parents[3]
DNV = "doggiesnewvehicle/"
PUBLIC_DOCS = DNV + "docs/public/"
ROOT_FILES = {PUBLIC_DOCS + "README.md": "README.md", PUBLIC_DOCS + "gitignore": ".gitignore"}
DNV_FILES = {
    "build.gradle", "build-neoforge.gradle", "gradle.properties", "settings.gradle",
    "gradlew", "gradlew.bat", "docs/corresponding-source.md",
    "tools/ap-runtime/prepare_inputs.py", "tools/aviation/pack_meshes.py",
    "tools/release/package_sources.py",
}
DNV_DIRS = ("src/", "route-core/", "gradle/", "docs/curseforge/", "docs/public/")
ARCHITECTURE = "architecture-test-support/"
ARCHITECTURE_MAPPING = ARCHITECTURE + "fixtures/ratchet/MODULE-MAPPING.tsv"
SHARED_TEST_FILES = {
    ARCHITECTURE_MAPPING,
    ARCHITECTURE + "fixtures/ratchet/baselines/dnv.tsv",
    "tools/dnv-mothership-fx-preview/field-audio-mix.json",
}
WRAPPER = DNV + "gradle/wrapper/gradle-wrapper.jar"
NEGATIVE_CONTROL = DNV + "src/test/resources/fixtures/field-feedback/ApocalypseMissile-before.class"
PIN_FILE = DNV + "gradle/neoforge1211/dependencies.json"
RUNTIME_ART_INPUTS = DNV + "gradle/runtime-art-inputs.txt"
HEXBOT_NOTICE = DNV + "src/artillery/resources/assets/doggiesnewvehicle/models/hexbot-CREDITS.txt"
AUTHOR_EXTENSIONS = {
    ".blend", ".blend1", ".bbmodel", ".fbx", ".glb", ".gltf", ".obj", ".mtl", ".stl",
    ".3ds", ".dae", ".ply", ".psd", ".kra",
}
ART_EXTENSIONS = {
    ".bin", ".etm", ".shm", ".nsm", ".dsm", ".png", ".jpeg", ".jpg", ".webp",
    ".tga", ".gif", ".dds", ".svg", ".ogg", ".wav", ".mp3", ".flac",
}
ART_FOLDERS = {"models", "geo", "animations", "textures", "sounds"}
PRIVATE_DIRS = {
    ".git", ".gradle", ".idea", ".codex", ".minecraft", "__pycache__", "build",
    "model-source", "libs", "logs", "run", "saves", "crash-reports",
}
PRIVATE_EXTENSIONS = {".jar", ".class", ".zip", ".7z", ".rar", ".log", ".jks", ".pfx", ".pem", ".key"}


def is_art_asset(name):
    path = PurePosixPath(name)
    if path.suffix.lower() in ART_EXTENSIONS:
        return True
    if name == HEXBOT_NOTICE or "/assets/" not in name:
        return False
    asset_path = name.split("/assets/", 1)[1].lower().split("/")
    return path.name == "model-sources.json" or (len(asset_path) > 2 and asset_path[1] in ART_FOLDERS)


def included(name):
    path = PurePosixPath(name)
    suffix = path.suffix.lower()
    if name in {WRAPPER, NEGATIVE_CONTROL}:
        return True
    if suffix in AUTHOR_EXTENSIONS | PRIVATE_EXTENSIONS or PRIVATE_DIRS.intersection(part.lower() for part in path.parts):
        return False
    if path.name.startswith(".env"):
        return False
    if name.startswith(DNV):
        relative = name[len(DNV):]
        return relative in DNV_FILES or relative.startswith(DNV_DIRS)
    return (name == "LICENSE" or name in SHARED_TEST_FILES
            or name.startswith(("libertad/src/", ARCHITECTURE + "src/test/java/")))


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


def source_entries(commit):
    selected = {}
    withheld = []
    kept_roots = {"README.md", ".gitignore"}
    monorepo = bool(git("ls-tree", "-d", commit, "--", "doggiecasterhud-classic-clean"))
    entries = git("ls-tree", "-r", "-z", commit, "--", "LICENSE", DNV, "libertad/src",
                  ARCHITECTURE, "tools/dnv-mothership-fx-preview", *kept_roots)
    for entry in entries.split(b"\0")[:-1]:
        header, raw_name = entry.split(b"\t", 1)
        mode, kind, blob = header.split()
        name = raw_name.decode("utf-8")
        if not included(name) and (monorepo or name not in kept_roots):
            continue
        if mode not in {b"100644", b"100755"} or kind != b"blob" or any(c in name for c in "\\:"):
            raise ValueError(f"Unsupported source entry: {name}")
        if is_art_asset(name):
            if "/resources/assets/" in name and "/src/test/" not in name:
                withheld.append(name[len(DNV):] if name.startswith(DNV) else "../" + name)
            continue
        public_name = ROOT_FILES.get(name, name)
        if public_name in selected:
            raise ValueError(f"Duplicate source entry: {public_name}")
        selected[public_name] = (blob, mode)
    required = kept_roots | {
        "LICENSE", WRAPPER, DNV + "src/main/resources/META-INF/DNV-LICENSES.md",
        HEXBOT_NOTICE,
    }
    if missing := required - selected.keys():
        raise ValueError(f"Missing required source entries: {', '.join(sorted(missing))}")
    if not withheld and RUNTIME_ART_INPUTS not in selected:
        raise ValueError("Missing runtime art input list")
    if withheld and RUNTIME_ART_INPUTS in selected:
        raise ValueError("Export a code-only commit before adding any local runtime art")
    return selected, sorted(withheld)


def public_data(name, data):
    if name == ARCHITECTURE_MAPPING:
        lines = data.splitlines(keepends=True)
        return b"".join(line for line in lines if line.startswith((b"scope\t", b"dnv\t")))
    if name == PIN_FILE:
        pins = json.loads(data)
        for pin in pins:
            pin.pop("installed_source", None)
        return (json.dumps(pins, indent=2) + "\n").encode("utf-8")
    if name.startswith(DNV + "src/test/resources/audited-exports/") and name.endswith("_export_report.json"):
        report = json.loads(data)
        for key in ("source_blend", "output_mesh", "mesh"):
            if key in report:
                report[key] = report[key].replace("\\", "/").rsplit("/", 1)[-1]
        return (json.dumps(report, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    return data


def make_archive(entries, withheld):
    output = BytesIO()
    with (ZipFile(output, "w", compression=ZIP_DEFLATED, compresslevel=9) as archive,
          subprocess.Popen(["git", "cat-file", "--batch"], cwd=ROOT,
                           stdin=subprocess.PIPE, stdout=subprocess.PIPE) as objects):
        for name, (blob, mode) in sorted(entries.items()):
            objects.stdin.write(blob + b"\n")
            objects.stdin.flush()
            header = objects.stdout.readline().split()
            if len(header) != 3 or header[:2] != [blob, b"blob"]:
                raise ValueError(f"Unavailable Git blob: {name}")
            size = int(header[2])
            data = objects.stdout.read(size)
            if len(data) != size or objects.stdout.read(1) != b"\n":
                raise ValueError(f"Incomplete Git blob: {name}")
            entry = ZipInfo(name)
            entry.create_system = 3
            entry.external_attr = (0o100755 if name == DNV + "gradlew" else int(mode, 8)) << 16
            archive.writestr(entry, public_data(name, data), compress_type=ZIP_DEFLATED, compresslevel=9)
        if withheld:
            archive.writestr(ZipInfo(RUNTIME_ART_INPUTS), "\n".join(withheld) + "\n",
                             compress_type=ZIP_DEFLATED, compresslevel=9)
        objects.stdin.close()
        if objects.wait():
            raise RuntimeError("Git blob reader failed")
    with ZipFile(output) as archive:
        expected = sorted(entries) + ([RUNTIME_ART_INPUTS] if withheld else [])
        if archive.namelist() != expected or archive.testzip() is not None:
            raise RuntimeError("Source archive verification failed")
    return output.getbuffer()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("--ref", default="HEAD", help="Git commit, branch or tag (default: HEAD)")
    args = parser.parse_args()
    commit = git("rev-parse", "--verify", "--end-of-options", args.ref + "^{commit}").decode().strip()
    entries, withheld = source_entries(commit)
    data = make_archive(entries, withheld)
    with args.output.open("xb") as output:
        output.write(data)
    print(f"{len(entries) + bool(withheld)} files from {commit}\nSHA-256 {sha256(data).hexdigest()}\n{args.output}")


if __name__ == "__main__":
    main()
