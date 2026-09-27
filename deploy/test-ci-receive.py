"""Test the actual receiver with isolated filesystem, network and service mocks."""
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

SOURCE = Path(__file__).with_name('ci-receive.sh').read_text()
REVISION = 'a' * 40


class ReceiverTest(unittest.TestCase):
    def run_receiver(self, scenario):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            base, mock = root / 'app', root / 'bin'
            (base / 'releases').mkdir(parents=True)
            mock.mkdir()
            previous = base / 'releases' / 'previous.jar'
            previous_revision = 'c' * 40
            with zipfile.ZipFile(previous, 'w') as jar:
                jar.writestr('BOOT-INF/classes/static/index.html', 'Previous page')
                if scenario != 'legacy':
                    jar.writestr('BOOT-INF/classes/static/version.json', json.dumps({'commit': previous_revision}))
            previous_bytes = previous.read_bytes()
            served = root / 'served'
            served.write_text(previous_revision)
            current = base / 'current.jar'
            current.symlink_to(previous)
            calls = root / 'calls'
            commands = {
                'curl': '''#!/bin/bash
case "${@: -1}" in
  */commits/main) printf '{"sha":"%s"}' "$MAIN_SHA";;
  */actuator/health)
    [[ -s "$SERVED" ]] || exit 7
    if [[ "$UNHEALTHY" == yes ]]; then exit 22; fi
    if [[ "$RECOVER" == yes && $(grep -c 'restart postcompare' "$CALLS") -lt 2 ]]; then exit 22; fi
    printf '{"status":"UP"}';;
  */version.json) [[ -s "$SERVED" ]] || exit 7; printf '{"commit":"%s"}' "$(cat "$SERVED")";;
  http://127.0.0.1:8080/) printf 'Previous page';;
  *) exit 1;;
esac
''',
                'systemctl': '''#!/bin/bash
printf '%s\\n' "$*" >> "$CALLS"
if [[ "$1" == restart ]]; then
  if [[ "$RESTART_FAIL" == yes ]]; then : > "$SERVED"; exit 7; fi
  if [[ "$(readlink "$CURRENT")" == */previous.jar && "$STUCK" != yes ]]; then
    printf '%s' "$PREVIOUS_REVISION" > "$SERVED"
  else
    printf '%s' "$MAIN_SHA" > "$SERVED"
  fi
fi
''',
                'sleep': '#!/bin/bash\nexit 0\n',
            }
            for name, body in commands.items():
                path = mock / name
                path.write_text(body)
                path.chmod(0o700)
            script = SOURCE.replace('[[ $EUID -eq 0 && $# -eq 1 ]]', '[[ $# -eq 1 ]]').replace('base=/opt/postcompare', 'base=' + str(base))
            receiver = root / 'receiver.sh'
            receiver.write_text(script)
            stream = io.BytesIO()
            with zipfile.ZipFile(stream, 'w') as jar:
                jar.writestr('BOOT-INF/classes/static/index.html', 'PostCompare')
                jar.writestr('BOOT-INF/classes/static/version.json', json.dumps({'commit': 'b' * 40 if scenario == 'wrong-metadata' else REVISION}))
            artifact = stream.getvalue()
            digest = '0' * 64 if scenario == 'wrong-hash' else hashlib.sha256(artifact).hexdigest()
            command = 'deploy ' + REVISION + ' ' + digest
            if scenario == 'invalid-command':
                command = 'deploy ' + REVISION + '; touch /tmp/never-created'
            env = dict(os.environ, PATH=str(mock) + ':' + os.environ['PATH'], CALLS=str(calls), MAIN_SHA='b' * 40 if scenario == 'stale' else REVISION, UNHEALTHY='yes' if scenario == 'unhealthy' else 'no')
            env.update(CURRENT=str(current), SERVED=str(served), PREVIOUS_REVISION=previous_revision,
                       RECOVER='yes' if scenario in ('recovered', 'wrong-rollback-revision', 'legacy') else 'no',
                       STUCK='yes' if scenario == 'wrong-rollback-revision' else 'no',
                       RESTART_FAIL='yes' if scenario == 'restart-failed' else 'no')
            result = subprocess.run(['bash', str(receiver), command], input=artifact, env=env, capture_output=True)
            if scenario == 'success':
                self.assertEqual(result.returncode, 0, result.stderr.decode())
                self.assertNotEqual(current.resolve(), previous)
                self.assertEqual(current.read_bytes(), artifact)
                self.assertEqual(previous.read_bytes(), previous_bytes)
            else:
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(current.resolve(), previous)
                if scenario in ('unhealthy', 'recovered', 'restart-failed', 'wrong-rollback-revision', 'legacy'):
                    self.assertEqual(calls.read_text().count('restart postcompare'), 2)
                    expected_message = ('Previous JAR is not healthy' if scenario in ('unhealthy', 'wrong-rollback-revision', 'restart-failed')
                                        else 'legacy rollback revision cannot be verified' if scenario == 'legacy' else 'Previous JAR healthy')
                    self.assertIn(expected_message, result.stderr.decode())
                    if scenario == 'wrong-rollback-revision':
                        self.assertEqual(served.read_text(), REVISION)
                        self.assertNotIn('Previous JAR healthy', result.stderr.decode())
                    if scenario == 'restart-failed':
                        self.assertEqual(result.returncode, 7)
                        self.assertIn('Rollback restart failed', result.stderr.decode())
                else:
                    self.assertFalse(calls.exists())
            self.assertEqual(list((base / 'releases').glob('.incoming.*')), [])

    def test_receiver(self):
        for scenario in ('success', 'invalid-command', 'stale', 'wrong-hash', 'wrong-metadata', 'unhealthy', 'recovered', 'restart-failed', 'wrong-rollback-revision', 'legacy'):
            with self.subTest(scenario=scenario):
                self.run_receiver(scenario)


if __name__ == '__main__':
    unittest.main()
