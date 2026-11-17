"""Fail CI for known Maven vulnerabilities in the resolved full dependency graph."""
import json
from pathlib import Path
import sys
import urllib.request


def audit(components):
    if not components:
        raise ValueError('Dependency inventory is empty')
    findings = []
    for offset in range(0, len(components), 500):
        batch = components[offset:offset + 500]
        queries = []
        for component in batch:
            group, artifact, version = component.split(':', 2)
            queries.append({'package': {'ecosystem': 'Maven', 'name': f'{group}:{artifact}'},
                            'version': version})
        request = urllib.request.Request('https://api.osv.dev/v1/querybatch',
            data=json.dumps({'queries': queries}).encode(),
            headers={'Content-Type': 'application/json'})
        with urllib.request.urlopen(request, timeout=60) as response:
            results = json.load(response)['results']
        if len(results) != len(batch):
            raise ValueError('OSV returned an incomplete audit')
        for component, result in zip(batch, results):
            for vulnerability in result.get('vulns', []):
                findings.append((component, vulnerability['id']))
    return findings


if __name__ == '__main__':
    components = json.loads(Path(sys.argv[1]).read_text())
    findings = audit(components)
    for component, advisory in findings:
        print(f'{component}: https://osv.dev/vulnerability/{advisory}')
    print(f'Audited {len(components)} resolved Maven components; {len(findings)} known advisories')
    sys.exit(1 if findings else 0)
