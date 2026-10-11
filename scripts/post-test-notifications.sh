#!/usr/bin/env bash
# Posts synthetic test notifications through the Android shell (no helper app needed).
# They appear to come from the shell package, so Scam Monitor treats them as another app's notifications.
# Requires: adb with a connected device/emulator. Best effort: `cmd notification post` options vary by Android version;
# the Test Sender app (testsender module) is the primary, reproducible method.
set -euo pipefail

post() { adb shell cmd notification post -S bigtext -t "$1" "$2" "$3"; }

post "MoMo Support" scam-pin   "Please send your MoMo PIN to complete the verification of your wallet."
post "Notice"       scam-block "URGENT: confirm now at http://blocked.example/x"
post "MoMo"         normal-tx  "You have sent GHS 50.00 to TEST PERSON 0200000000. Transaction ID 000000001. New balance GHS 120.50."
