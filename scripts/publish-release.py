#!/usr/bin/env python3
"""Bump the Android version, commit, tag, and push to trigger both release APKs."""
import argparse
from pathlib import Path
import re
import subprocess
import sys


class ReleaseError(Exception):
    pass


def git(*args, capture=True):
    result = subprocess.run(['git', *args], text=True, capture_output=capture)
    if result.returncode:
        detail = (result.stderr or '').strip()
        raise ReleaseError(f"git {' '.join(args)} failed. {detail}")
    return result.stdout.strip() if capture else ''


def version_key(version):
    match = re.fullmatch(
        r'(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)'
        r'(?:-([0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*))?', version,
    )
    if not match:
        raise ReleaseError(f'Invalid version: {version}. Use X.Y.Z or X.Y.Z-beta.1 (without v).')
    identifiers = (match[4] or '').split('.') if match[4] else []
    if any(part.isdigit() and len(part) > 1 and part.startswith('0') for part in identifiers):
        raise ReleaseError('Numeric prerelease identifiers cannot have leading zeroes.')
    prerelease = tuple((0, int(part)) if part.isdigit() else (1, part) for part in identifiers)
    return (*map(int, match.group(1, 2, 3)), not bool(identifiers), prerelease)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('version', nargs='?', help='new version; default: increment patch number')
    parser.add_argument('--dry-run', action='store_true', help='check and preview without modifying files or pushing')
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    # All Git commands and paths belong to this checkout, regardless of invocation directory.
    import os
    os.chdir(root)
    if Path(git('rev-parse', '--show-toplevel')).resolve() != root:
        raise ReleaseError('Run the script from its original repository.')
    if git('branch', '--show-current') != 'main':
        raise ReleaseError('Switch to main before publishing.')
    if git('status', '--porcelain', '--untracked-files=all'):
        raise ReleaseError('Working tree is not clean. Commit or save your changes first.')
    path = root / 'app/build.gradle.kts'
    text = path.read_text(encoding='utf-8')
    names = list(re.finditer(r'(?m)^\s*versionName\s*=\s*"([^"]+)"', text))
    codes = list(re.finditer(r'(?m)^\s*versionCode\s*=\s*([0-9]+)\s*$', text))
    if len(names) != 1 or len(codes) != 1:
        raise ReleaseError('Expected exactly one versionName and one versionCode in app/build.gradle.kts.')
    old = names[0][1]
    old_key = version_key(old)
    new = args.version or f'{old_key[0]}.{old_key[1]}.{old_key[2] + 1}'
    if version_key(new) <= old_key:
        raise ReleaseError(f'New version {new} must be greater than {old}.')
    code = int(codes[0][1]) + 1
    if not 1 <= code <= 2100000000:
        raise ReleaseError('Next versionCode is outside the Android supported range.')
    tag = f'v{new}'
    if git('tag', '--list', tag):
        raise ReleaseError(f'Local tag {tag} already exists. Choose a new version.')
    refs = git('ls-remote', '--refs', 'origin', 'refs/heads/main', f'refs/tags/{tag}')
    for line in refs.splitlines():
        oid, ref = line.split()
        if ref == f'refs/tags/{tag}':
            raise ReleaseError(f'Remote tag {tag} already exists. Choose a new version.')
        if ref == 'refs/heads/main':
            result = subprocess.run(['git', 'merge-base', '--is-ancestor', oid, 'HEAD'], capture_output=True)
            if result.returncode:
                raise ReleaseError('Local main does not contain remote main. Fetch and integrate it first.')
    push_args = ['push', '--atomic', 'origin', 'HEAD:refs/heads/main', f'refs/tags/{tag}:refs/tags/{tag}']
    print(f'Version: {old} -> {new}; versionCode: {code - 1} -> {code}', flush=True)
    print(f'Commit and tag: {tag}; push main and tag atomically to origin.', flush=True)
    print(f'Actions will publish WeReadLyrics-{tag}.apk, WeReadLyrics-{tag}-debug.apk and SHA256SUMS.', flush=True)
    if args.dry_run:
        print('Dry run complete. No files, commits, tags or remote refs changed.')
        return
    # Replace only the matched values, preserving the rest of the Gradle file.
    for match, value in sorted([(names[0], new), (codes[0], str(code))], key=lambda item: item[0].start(1), reverse=True):
        text = text[:match.start(1)] + value + text[match.end(1):]
    path.write_text(text, encoding='utf-8')
    git('diff', '--check')
    git('add', '--', 'app/build.gradle.kts')
    git('commit', '-m', f'chore: release {tag}', capture=False)
    git('tag', '-a', tag, '-m', f'Release {tag}', capture=False)
    try:
        git(*push_args, capture=False)
    except ReleaseError:
        raise ReleaseError(
            'Push failed; the local release commit and tag were kept. '
            'After resolving the push failure, retry this command instead of bumping again:\n'
            f"git {' '.join(push_args)}"
        ) from None
    print('Push complete. GitHub Actions still needs to build and publish successfully.')
    print('Check the repository Actions page; the four signing Secrets described in README are required.')


if __name__ == '__main__':
    try:
        main()
    except (ReleaseError, OSError) as error:
        print(f'Error: {error}', file=sys.stderr)
        sys.exit(1)
