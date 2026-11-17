"""Open fix PRs for default-branch CodeQL alerts using GitHub's Autofix API.

Never checks out or executes generated fixes. Unsupported alerts become issues.
The workflow dispatches checks explicitly because GITHUB_TOKEN-created PRs do
not trigger pull_request workflows. No credentials beyond GH_TOKEN are needed.
"""
import json
import os
import subprocess


def api(endpoint, method='GET', data=None):
    command = ['gh', 'api', '--method', method, endpoint]
    if data is not None:
        command += ['--input', '-']
    result = subprocess.run(command, input=json.dumps(data) if data is not None else None,
                            text=True, capture_output=True, check=True)
    return json.loads(result.stdout) if result.stdout.strip() else None


def pages(endpoint):
    page = 1
    while True:
        rows = api(f'{endpoint}{"&" if "?" in endpoint else "?"}per_page=100&page={page}')
        yield from rows
        if len(rows) < 100:
            return
        page += 1


def issue_for_alert(repo, alert, existing):
    title = f'Security: review CodeQL alert #{alert["number"]}'
    if title not in existing:
        api(f'repos/{repo}/issues', 'POST', {
            'title': title,
            'body': f'CodeQL alert: {alert["html_url"]}\n\nGitHub Autofix could not generate '
                    'a patch. A manual fix and a pull request are required. '
                    'The alert remains open; this workflow does not dismiss it.'})
        existing.add(title)


def remediate(repo):
    settings = api(f'repos/{repo}')
    default = settings['default_branch']
    base = api(f'repos/{repo}/git/ref/heads/{default}')['object']['sha']
    pulls = list(pages(f'repos/{repo}/pulls?state=open'))
    issues = {row['title'] for row in pages(f'repos/{repo}/issues?state=all')}
    for alert in pages(f'repos/{repo}/code-scanning/alerts?state=open&tool_name=CodeQL'):
        number = alert['number']
        branch = f'security/codeql-{number}-{base[:12]}'
        # One active fix PR per alert, even if master advanced since it opened.
        if any(pr['head']['ref'].startswith(f'security/codeql-{number}-') for pr in pulls):
            continue
        endpoint = f'repos/{repo}/code-scanning/alerts/{number}/autofix'
        try:
            fix = api(endpoint, 'POST')
        except subprocess.CalledProcessError as error:
            if any(f'HTTP {code}' in error.stderr for code in (400, 403, 404, 422)):
                issue_for_alert(repo, alert, issues)
                continue
            raise
        if fix['status'] in ('pending', 'generating'):
            print(f'Autofix for alert #{number} is generating; next run will collect it.')
            continue
        if fix['status'] != 'success':
            issue_for_alert(repo, alert, issues)
            continue
        # Resume an existing branch after a partial run rather than failing forever.
        try:
            api(f'repos/{repo}/git/ref/heads/{branch}')
        except subprocess.CalledProcessError as error:
            if 'HTTP 404' not in error.stderr:
                raise
            api(f'repos/{repo}/git/refs', 'POST', {'ref': f'refs/heads/{branch}', 'sha': base})
        tip = api(f'repos/{repo}/git/ref/heads/{branch}')['object']['sha']
        if tip == base:
            api(endpoint + '/commits', 'POST', {
                'target_ref': f'refs/heads/{branch}',
                'message': f'Fix CodeQL alert #{number}'})
        pr = api(f'repos/{repo}/pulls', 'POST', {
            'title': f'Fix CodeQL alert #{number}', 'head': branch, 'base': default,
            'body': f'GitHub Autofix patch for {alert["html_url"]}.\n\n'
                    'Review the generated patch and passing CI/CodeQL results before merging. '
                    'The workflow does not dismiss alerts or bypass branch protection.'})
        print(f'Created {pr["html_url"]}')
        for workflow in ('ci.yml', 'codeql.yml'):
            api(f'repos/{repo}/actions/workflows/{workflow}/dispatches', 'POST', {'ref': branch})


if __name__ == '__main__':
    remediate(os.environ['GITHUB_REPOSITORY'])
