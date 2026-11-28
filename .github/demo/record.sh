#!/bin/bash
# usage: record.sh  (needs DEMO_EMAIL, DEMO_PASS env). Runs inside the emulator.
mkdir -p clips dumps
P=com.wavy.app
rec_start(){ adb shell "screenrecord --time-limit 170 /sdcard/$1.mp4" & RP=$!; sleep 2; }
rec_stop(){ adb shell pkill -2 screenrecord; sleep 3; wait $RP 2>/dev/null; adb pull /sdcard/$1.mp4 clips/$1.mp4 >/dev/null; }
snap(){ adb exec-out screencap -p > dumps/$1.png; python3 .github/demo/ui.py find 'zzzz' >/dev/null 2>&1; adb exec-out cat /sdcard/u.xml > dumps/$1.xml 2>/dev/null; }
tap(){ adb shell input tap $1 $2; }
tapt(){ for i in 1 2 3 4 5 6; do c=$(python3 .github/demo/ui.py find "$1") && { tap $c; return 0; }; sleep 2; done; echo "NOT FOUND $1"; return 1; }
swipe_up(){ adb shell input swipe 540 1700 540 600 700; }
adb shell settings put global window_animation_scale 1; adb shell settings put global transition_animation_scale 1; adb shell settings put global animator_duration_scale 1
adb install -r wavy.apk | tail -1; adb shell pm grant $P android.permission.POST_NOTIFICATIONS 2>/dev/null; adb shell cmd statusbar collapse
adb shell monkey -p $P -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 12; snap s0_launch
# ---- login clip
rec_start 01_login
tapt 'Get Started'; sleep 4; snap s1_after_start
for k in 1 2 3; do
  if c=$(python3 .github/demo/ui.py edit 0); then break; fi
  tapt 'Continue with email|Sign in with email|Email|Log in|Sign in' ; sleep 3
done
snap s2_login
c=$(python3 .github/demo/ui.py edit 0) && { tap $c; sleep 1; adb shell input text "$DEMO_EMAIL"; sleep 1; }
c=$(python3 .github/demo/ui.py edit 1) && { tap $c; sleep 1; adb shell input text "$DEMO_PASS"; sleep 1; }
adb shell input keyevent 111 >/dev/null 2>&1
snap s3_filled
tapt '^Sign in$'; sleep 12
snap s4_after_login
for k in 1 2 3; do sleep 6; snap s5_wait$k; done
rec_stop 01_login
# ---- home
rec_start 02_home
sleep 3; for i in 1 2 3 4 5 6; do swipe_up; sleep 2.5; done
for i in 1 2 3 4 5 6; do adb shell input swipe 540 600 540 1800 600; sleep 1.5; done
snap s6_home
rec_stop 02_home
# ---- nav probe
rec_start 03_search
tapt '^Search$'; sleep 3; snap s7_search
rec_stop 03_search
rec_start 04_library
tapt '^Library$'; sleep 3; snap s8_library
rec_stop 04_library
adb logcat -d > dumps/logcat.txt; ls -la clips
