import subprocess
import unittest
from unittest.mock import patch
import codeql_fix_prs as fixes


class RemediationTests(unittest.TestCase):
    def run_case(self, status='success', duplicate=False, unsupported=False):
        calls = []
        alert = {'number': 7, 'html_url': 'https://github.com/owner/repo/security/code-scanning/7'}
        def request(endpoint, method='GET', data=None):
            calls.append((endpoint, method, data))
            if endpoint == 'repos/owner/repo':
                return {'default_branch': 'master'}
            if '/git/ref/' in endpoint:
                return {'object': {'sha': 'a' * 40}}
            if '/pulls?' in endpoint:
                return [{'head': {'ref': 'security/codeql-7-oldbase'}}] if duplicate else []
            if '/issues?' in endpoint:
                return []
            if '/alerts?' in endpoint:
                return [alert]
            if endpoint.endswith('/autofix'):
                if unsupported:
                    raise subprocess.CalledProcessError(1, ['gh'], stderr='HTTP 422')
                return {'status': status}
            if endpoint.endswith('/pulls'):
                return {'html_url': 'https://github.com/owner/repo/pull/1'}
            return {}
        with patch.object(fixes, 'api', side_effect=request):
            fixes.remediate('owner/repo')
        return calls

    def test_success_creates_pr_and_dispatches_both_checks(self):
        calls = self.run_case()
        self.assertEqual(1, sum(e.endswith('/pulls') and m == 'POST' for e, m, _ in calls))
        dispatches = [d['ref'] for e, _, d in calls if e.endswith('/dispatches')]
        self.assertEqual(['security/codeql-7-aaaaaaaaaaaa'] * 2, dispatches)
        self.assertFalse(any('dismiss' in str(d) for _, _, d in calls if isinstance(d, dict) and 'state' in d))

    def test_pending_does_not_create_pr_or_commit(self):
        calls = self.run_case(status='pending')
        self.assertFalse(any(e.endswith(('/pulls', '/commits')) for e, _, _ in calls))

    def test_existing_fix_pr_is_not_duplicated(self):
        calls = self.run_case(duplicate=True)
        self.assertFalse(any(e.endswith('/autofix') for e, _, _ in calls))

    def test_unsupported_fix_creates_issue_without_closing_alert(self):
        calls = self.run_case(unsupported=True)
        self.assertTrue(any(e.endswith('/issues') and m == 'POST' for e, m, _ in calls))
        self.assertFalse(any(m == 'PATCH' for _, m, _ in calls))


if __name__ == '__main__':
    unittest.main()
