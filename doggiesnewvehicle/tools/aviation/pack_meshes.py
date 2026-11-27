"""Pack or verify CMH1 meshes from the private aviation OBJ and MTL exports."""

import argparse
import math
from pathlib import Path
import struct


AVIATION = Path(__file__).resolve().parents[2] / "src/aviation"
SOURCE = AVIATION / "model-source/shanyue"
RUNTIME = AVIATION / "resources/assets/dnvcarriercube/models/shanyue"


def records(path):
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if line.strip() and not line.lstrip().startswith("#"):
            kind, *fields = line.split()
            yield f"{path.name}:{number}", kind, fields


def vector(fields, size):
    values = tuple(map(float, fields))
    if len(values) != size or not all(map(math.isfinite, values)):
        raise ValueError(f"Expected {size} finite coordinates: {fields}")
    return values


def materials(path):
    colors = {}
    textures = {}
    current = None
    for at, kind, fields in records(path):
        if kind == "newmtl":
            current, = fields
        elif kind == "Kd" and current:
            colors[current] = vector(fields, 3)
        elif kind == "Ka" and current:
            vector(fields, 3)  # These models bake with ambient emission disabled.
        elif kind == "map_Kd" and current:
            texture, = fields
            if not texture.startswith("#"):
                raise ValueError(f"{at}: expected a texture reference")
            textures[current] = texture[1:].encode("ascii")
        else:
            raise ValueError(f"{at}: unsupported material record {kind}")
    if not colors or colors.keys() != textures.keys():
        raise ValueError(f"{path.name}: mismatched diffuse colors and texture references")
    return {name: (textures[name], color) for name, color in colors.items()}


def pack_model(obj):
    mtl = obj.with_suffix(".mtl")
    surfaces = materials(mtl)
    material_ids = {name: i for i, name in enumerate(surfaces)}
    tables = {"v": [], "vt": [], "vn": []}
    widths = {"v": 3, "vt": 2, "vn": 3}
    faces = []
    surface = None
    for at, kind, fields in records(obj):
        if kind in tables:
            tables[kind].append(vector(fields, widths[kind]))
        elif kind == "mtllib" and fields == [mtl.name]:
            continue
        elif kind == "usemtl":
            name, = fields
            surface = material_ids[name]
        elif kind == "f" and len(fields) == 3 and surface is not None:
            row = [surface]
            for corner in fields:
                coordinates = tuple(int(index) - 1 for index in corner.split("/"))
                if len(coordinates) != 3 or any(
                    not 0 <= index < len(table) for index, table in zip(coordinates, tables.values())
                ):
                    raise ValueError(f"{at}: invalid position, UV or normal index")
                row.extend(coordinates)
            faces.append(row)
        else:
            raise ValueError(f"{at}: unsupported geometry record {kind}")

    if not faces:
        raise ValueError(f"{obj.name}: no triangles")
    output = bytearray(b"CMH1")
    for kind, table in tables.items():
        output.extend(struct.pack(">H", len(table)))
        for row in table:
            output.extend(struct.pack(f">{widths[kind]}f", *row))
    output.extend(struct.pack(">H", len(surfaces)))
    for texture, color in surfaces.values():
        output.extend(struct.pack(">H", len(texture)))
        output.extend(texture)
        output.extend(struct.pack(">3f", *color))
    output.extend(struct.pack(">H", len(faces)))
    for face in faces:
        output.extend(struct.pack(">10H", *face))
    return bytes(output), len(faces)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="compare runtime bytes without writing")
    args = parser.parse_args()
    models = sorted(SOURCE.glob("*.obj"))
    if not models:
        parser.error(f"no private model exports under {SOURCE}")
    for obj in models:
        data, triangles = pack_model(obj)
        target = RUNTIME / obj.with_suffix(".bin").name
        if args.check:
            if not target.is_file() or target.read_bytes() != data:
                parser.error(f"stale runtime mesh: {target.name}")
        else:
            target.write_bytes(data)
        print(f"{target.name}: {triangles} triangles, {len(data)} bytes")


if __name__ == "__main__":
    main()
