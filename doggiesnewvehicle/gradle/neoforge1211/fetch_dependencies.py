"""Restore pinned upstream build inputs; never overwrite an existing mismatched file."""
from pathlib import Path
import argparse, hashlib, json, urllib.request

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--download', action='store_true')
    args = parser.parse_args()
    rules = Path(__file__).resolve().parent
    dest = rules.parents[1] / 'libs/neoforge1211'
    dest.mkdir(parents=True, exist_ok=True)
    for pin in json.loads((rules/'dependencies.json').read_text(encoding='utf-8')):
        target = dest/pin['file']
        assert target.parent == dest and target.suffix == '.jar'
        if target.exists(): data = target.read_bytes()
        elif pin.get('installed_source'):
            data = Path(pin['installed_source']).read_bytes()
        elif args.download and pin.get('url'):
            assert pin['url'].startswith('https://cdn.modrinth.com/data/Cd3DYqzn/versions/')
            with urllib.request.urlopen(pin['url'], timeout=180) as response: data = response.read()
        else:
            raise FileNotFoundError(f'{target}: place the official file at this path (see docs/corresponding-source.md)')
        for algorithm in ['sha256','sha512']:
            actual = hashlib.new(algorithm, data).hexdigest()
            if actual != pin[algorithm]: raise ValueError(f'{target}: {algorithm} mismatch; existing file preserved')
        if not target.exists():
            with target.open('xb') as output: output.write(data)
        print(f'{pin["profile"]}: verified {pin["sha256"]}')

if __name__ == '__main__': main()
