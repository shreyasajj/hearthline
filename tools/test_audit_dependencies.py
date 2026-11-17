import io
import json
import unittest
from unittest.mock import patch
from audit_dependencies import audit


class AuditTests(unittest.TestCase):
    @patch('audit_dependencies.urllib.request.urlopen')
    def test_reports_advisory_against_the_correct_resolved_version(self, urlopen):
        urlopen.return_value = io.BytesIO(json.dumps({'results': [{}, {'vulns': [{'id': 'GHSA-test'}]}]}).encode())
        self.assertEqual([('example:unsafe:1.0', 'GHSA-test')],
                         audit(['example:safe:2.0', 'example:unsafe:1.0']))
        sent = json.loads(urlopen.call_args.args[0].data)
        self.assertEqual('1.0', sent['queries'][1]['version'])
        self.assertEqual('example:unsafe', sent['queries'][1]['package']['name'])

    @patch('audit_dependencies.urllib.request.urlopen')
    def test_incomplete_audit_fails_closed(self, urlopen):
        urlopen.return_value = io.BytesIO(b'{"results": []}')
        with self.assertRaises(ValueError):
            audit(['example:library:1.0'])

    def test_empty_inventory_fails_closed(self):
        with self.assertRaises(ValueError):
            audit([])


if __name__ == '__main__':
    unittest.main()
