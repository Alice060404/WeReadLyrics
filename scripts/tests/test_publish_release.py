"""Exercise publication in disposable repositories; never contact GitHub."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / 'publish-release.py'


class PublishReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='wereadlyrics-publish-test-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.remote = self.root / 'remote.git'
        self.repo = self.root / 'repo'
        self.repo.mkdir()
        self.env = dict(os.environ, GIT_CONFIG_GLOBAL='/dev/null', GIT_CONFIG_NOSYSTEM='1')
        self.git('init', '--bare', str(self.remote))
        self.git('init', '-b', 'main')
        self.git('config', 'user.name', 'Release Test')
        self.git('config', 'user.email', 'release-test@example.invalid')
        self.git('remote', 'add', 'origin', str(self.remote))
        (self.repo / 'app').mkdir()
        self.gradle = self.repo / 'app/build.gradle.kts'
        self.gradle.write_text('android {\n    defaultConfig {\n        versionCode = 12\n        versionName = "0.10.0"\n    }\n}\n')
        (self.repo / 'scripts').mkdir()
        shutil.copy2(SCRIPT, self.repo / 'scripts/publish-release.py')
        self.git('add', '.')
        self.git('commit', '-m', 'Initial fixture')
        self.git('push', 'origin', 'main')
        self.initial = self.git('rev-parse', 'HEAD')

    def git(self, *args):
        return subprocess.run(['git', *args], cwd=self.repo, env=self.env, text=True,
                              capture_output=True, check=True).stdout.strip()

    def run_script(self, *args, succeeds=True):
        result = subprocess.run(['python3', str(self.repo / 'scripts/publish-release.py'), *args],
                                cwd=self.root, env=self.env, text=True, capture_output=True)
        self.assertEqual(result.returncode == 0, succeeds, result.stdout + result.stderr)
        return result

    def assert_untouched(self):
        self.assertEqual(self.git('rev-parse', 'HEAD'), self.initial)
        self.assertIn('versionCode = 12', self.gradle.read_text())
        self.assertEqual(self.git('--git-dir', str(self.remote), 'rev-parse', 'refs/heads/main'), self.initial)

    def test_default_publish_and_next_publish(self):
        self.run_script()
        self.assertIn('versionCode = 13', self.gradle.read_text())
        self.assertIn('versionName = "0.10.1"', self.gradle.read_text())
        head = self.git('rev-parse', 'HEAD')
        self.assertEqual(self.git('--git-dir', str(self.remote), 'rev-parse', 'refs/heads/main'), head)
        self.assertEqual(self.git('--git-dir', str(self.remote), 'rev-parse', 'v0.10.1^{}'), head)
        self.assertEqual(self.git('cat-file', '-t', 'v0.10.1'), 'tag')
        self.assertEqual(self.git('status', '--porcelain'), '')
        self.run_script()
        self.assertIn('versionCode = 14', self.gradle.read_text())
        self.assertIn('versionName = "0.10.2"', self.gradle.read_text())

    def test_dry_run_changes_nothing(self):
        before = self.git('ls-remote', 'origin')
        self.run_script('--dry-run')
        self.assert_untouched()
        self.assertEqual(self.git('status', '--porcelain'), '')
        self.assertEqual(self.git('tag', '--list'), '')
        self.assertEqual(self.git('ls-remote', 'origin'), before)

    def test_dirty_tree_and_wrong_branch_are_rejected(self):
        extra = self.repo / 'unfinished.txt'
        extra.write_text('unfinished')
        self.run_script(succeeds=False)
        self.assert_untouched()
        extra.unlink()
        self.git('checkout', '-b', 'feature')
        self.run_script(succeeds=False)
        self.assert_untouched()

    def test_existing_local_and_remote_tags_are_rejected(self):
        self.git('tag', 'v0.10.1')
        self.run_script(succeeds=False)
        self.assert_untouched()
        self.git('push', 'origin', 'v0.10.1')
        self.git('tag', '-d', 'v0.10.1')
        self.run_script(succeeds=False)
        self.assert_untouched()

    def test_remote_main_ahead_is_rejected(self):
        self.git('commit', '--allow-empty', '-m', 'Other publisher')
        self.git('push', 'origin', 'main')
        self.git('reset', '--hard', self.initial)
        self.run_script(succeeds=False)
        self.assertEqual(self.git('rev-parse', 'HEAD'), self.initial)
        self.assertIn('versionCode = 12', self.gradle.read_text())

    def test_atomic_push_failure_preserves_local_release_for_retry(self):
        hook = self.remote / 'hooks/pre-receive'
        hook.write_text('#!/bin/sh\nexit 1\n')
        hook.chmod(0o755)
        result = self.run_script(succeeds=False)
        self.assertIn('git push --atomic origin HEAD:refs/heads/main', result.stderr)
        self.assertIn('versionCode = 13', self.gradle.read_text())
        self.assertEqual(self.git('rev-parse', 'v0.10.1^{}'), self.git('rev-parse', 'HEAD'))
        self.assertEqual(self.git('--git-dir', str(self.remote), 'rev-parse', 'refs/heads/main'), self.initial)
        self.assertEqual(self.git('--git-dir', str(self.remote), 'tag', '--list'), '')
        hook.unlink()
        self.git('push', '--atomic', 'origin', 'HEAD:refs/heads/main', 'refs/tags/v0.10.1:refs/tags/v0.10.1')
        self.assertEqual(self.git('--git-dir', str(self.remote), 'rev-parse', 'v0.10.1^{}'), self.git('rev-parse', 'HEAD'))

    def test_version_validation_and_prerelease(self):
        for version in ['0.10.0', '0.9.9', 'v0.11.0', '0.11.0-beta.01', '1.2.3;false']:
            with self.subTest(version=version):
                self.run_script(version, succeeds=False)
                self.assert_untouched()
        self.run_script('0.11.0-beta.1')
        self.assertIn('versionName = "0.11.0-beta.1"', self.gradle.read_text())
        self.assertEqual(self.git('--git-dir', str(self.remote), 'rev-parse', 'v0.11.0-beta.1^{}'), self.git('rev-parse', 'HEAD'))


if __name__ == '__main__':
    unittest.main()
