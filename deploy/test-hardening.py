"""Exercise the generated rollback and firewall replacement with isolated command mocks.

Run on Linux: python3 deploy/test-hardening.py. No host configuration is changed.
"""
from pathlib import Path
import os
import subprocess
import tempfile
import time
import unittest

SOURCE = Path(__file__).with_name("harden-vps.sh").read_text()
ROLLBACK = SOURCE.split("<<'ROLLBACK'\n", 1)[1].split("\nROLLBACK", 1)[0]


class HardeningTest(unittest.TestCase):
    def test_restore_and_confirmation(self):
        for enabled in (False, True):
            for existing in (False, True):
                with self.subTest(enabled=enabled, existing=existing), tempfile.TemporaryDirectory() as tmp:
                    root = Path(tmp)
                    state, live, mock = root / "backup", root / "live", root / "bin"
                    for directory in (state / "ufw", live / "ufw", mock):
                        directory.mkdir(parents=True)
                    (state / "unit").write_text("test-rollback\n")
                    (state / "deadline").write_text(str(int(time.time()) + 300))
                    (state / "ufw" / "ufw.conf").write_text("ENABLED=" + ("yes" if enabled else "no") + "\n")
                    (state / "ufw" / "user.rules").write_text("previous firewall")
                    (state / "ufw-default").write_text("previous defaults")
                    (live / "ufw" / "user.rules").write_text("new firewall")
                    (live / "ssh-dropin").write_text("new ssh")
                    if existing:
                        (state / "ssh-dropin").write_text("previous ssh")
                    for command in ("sshd", "systemctl", "systemd-cat", "ufw"):
                        executable = mock / command
                        executable.write_text('#!/bin/sh\nprintf "%s\\n" "' + command + ' $*" >> "$TEST_LOG"\n')
                        executable.chmod(0o700)
                    script = ROLLBACK.replace("/etc/ssh/sshd_config.d/00-postcompare-hardening.conf", str(live / "ssh-dropin"))
                    script = script.replace("/etc/default/ufw", str(live / "ufw-default")).replace("/etc/ufw/", str(live / "ufw") + "/")
                    script = script.replace("/run/postcompare-hardening.lock", str(root / "lock"))
                    helper = state / "rollback.sh"
                    helper.write_text(script)
                    env = dict(os.environ, PATH=str(mock) + ":" + os.environ["PATH"], TEST_LOG=str(root / "calls"))
                    # A new timer process before the persisted deadline must not restore anything.
                    subprocess.run(["bash", str(helper)], env=env, check=True)
                    self.assertFalse((root / "calls").exists())
                    # A fresh process after a missed deadline (including a reboot) restores the snapshot.
                    (state / "deadline").write_text("1")
                    subprocess.run(["bash", str(helper)], env=env, check=True)
                    self.assertEqual((live / "ufw" / "user.rules").read_text(), "previous firewall")
                    self.assertEqual((live / "ufw-default").read_text(), "previous defaults")
                    self.assertEqual((live / "ssh-dropin").exists(), existing)
                    if existing:
                        self.assertEqual((live / "ssh-dropin").read_text(), "previous ssh")
                    self.assertIn("ufw --force " + ("enable" if enabled else "disable"), (root / "calls").read_text())
                    self.assertTrue((state / "restored").exists())
                    # A confirmed run cannot overwrite a later valid configuration.
                    (state / "restored").unlink()
                    (state / "confirmed").touch()
                    (live / "ssh-dropin").write_text("confirmed ssh")
                    (root / "calls").unlink()
                    subprocess.run(["bash", str(helper)], env=env, check=True)
                    self.assertEqual((live / "ssh-dropin").read_text(), "confirmed ssh")
                    self.assertNotIn("ufw", (root / "calls").read_text())

    def test_old_firewall_rules_removed_in_descending_order(self):
        # Exercise both actual deletion loops with representative UFW IPv4/IPv6 output.
        firewall = SOURCE.split("# --- Pare-feu\n", 1)[1].split("# --- SSH", 1)[0]
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            mock = root / "ufw"
            mock.write_text('''#!/usr/bin/env bash
if [[ "$1 $2" == "status numbered" ]]; then
  if [[ ! -f "$TEST_STATE" ]]; then
    touch "$TEST_STATE"
    printf '%s\n' '[ 1] 22/tcp ALLOW IN Anywhere # test-state-ssh' '[ 2] 5432/tcp ALLOW IN Anywhere' '[ 3] 22/tcp (v6) ALLOW IN Anywhere (v6) # test-state-ssh' '[ 4] 5432/tcp (v6) ALLOW IN Anywhere (v6)'
  else
    printf '%s\n' '[ 1] 22/tcp ALLOW IN Anywhere # test-state-ssh' '[ 2] 22/tcp LIMIT IN Anywhere' '[ 3] 22/tcp (v6) ALLOW IN Anywhere (v6) # test-state-ssh' '[ 4] 80/tcp ALLOW IN Anywhere'
  fi
else
  printf '%s\n' "$*" >> "$TEST_LOG"
fi
''')
            mock.chmod(0o700)
            env = dict(os.environ, PATH=str(root) + ":" + os.environ["PATH"], TEST_STATE=str(root / "status"), TEST_LOG=str(root / "calls"), STATE="/root/test-state", SSH_PORT="22", EXTRA_PORTS="8211/udp")
            subprocess.run(["bash", "-euc", firewall], env=env, check=True)
            calls = (root / "calls").read_text().splitlines()
            self.assertEqual([line for line in calls if line.startswith("--force delete")], ["--force delete 4", "--force delete 2", "--force delete 3", "--force delete 1"])
            self.assertLess(calls.index("insert 1 allow 22/tcp comment test-state-ssh"), calls.index("--force delete 4"))
            self.assertLess(calls.index("limit 22/tcp comment SSH"), calls.index("--force delete 3"))
            self.assertIn("allow 8211/udp", calls)


if __name__ == "__main__":
    unittest.main()
