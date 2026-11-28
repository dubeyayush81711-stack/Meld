#!/bin/bash
# usage: record.sh  (needs DEMO_EMAIL, DEMO_PASS env). Runs inside the emulator.
mkdir -p clips dumps
P=com.wavy.app
rec_start(){ adb shell "screenrecord --time-limit 170 /sdcard/$1.mp4" & RP=$!; sleep 2; }
rec_stop(){ adb shell pkill -2 screenrecord; sleep 3; wait $RP 2>/dev/null; adb pull /sdcard/$1.mp4 clips/$1.mp4 >/dev/null; }
snap(){ adb exec-out screencap -p > dumps/$1.png; python3 .github/demo/ui.py find 'zzzz' >/dev/null 2>&1; adb exec-out cat /sdcard/u.xml > dumps/$1.xml 2>/dev/null; }
typeslow(){ local s="$1"; adb shell input keyevent 123; for i in $(seq 1 40); do adb shell input keyevent 67; done; for ((i=0;i<${#s};i+=3)); do adb shell input text "${s:i:3}"; sleep 0.5; done; }
tapq(){ c=$(python3 .github/demo/ui.py find "$1") && { tap $c; return 0; }; return 1; }
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
c=$(python3 .github/demo/ui.py edit 0) && { tap $c; sleep 1; typeslow "$DEMO_EMAIL"; sleep 1; }
c=$(python3 .github/demo/ui.py edit 1) && { tap $c; sleep 1; typeslow "$DEMO_PASS"; sleep 1; }
snap s3_filled
adb shell input keyevent 4; sleep 2; tapt '^Sign in$'; sleep 14
snap s4_after_login
for k in 1 2 3 4 5 6 7 8 9 10 11 12; do
  sleep 3; snap ob$k
  tapq '^(Hindi|Punjabi|English)$'; tapq '^(Hindi|Punjabi|English)$' >/dev/null
  tapq '^(Get started|Let.s go|Start listening|Done|Finish|Continue|Next|Skip|Allow|OK|Create)' || break
done
sleep 8; snap s5_home_first
rec_stop 01_login
# ---- home
rec_start 02_home
sleep 3; adb shell dumpsys gfxinfo com.wavy.app reset >/dev/null; for i in 1 2 3 4 5 6; do swipe_up; sleep 2.5; done
for i in 1 2 3 4 5 6; do adb shell input swipe 540 600 540 1800 600; sleep 1.5; done
adb shell dumpsys gfxinfo com.wavy.app | grep -E "Total frames|Janky|percentile" > dumps/gfx_home.txt; snap s6_home
rec_stop 02_home
# ---- nav probe
rec_start 03_search
tapt '^Search$'; sleep 3; snap s7_search
rec_stop 03_search
rec_start 04_library
tapt '^Library$'; sleep 3; snap s8_library
rec_stop 04_library
for T in 'Home' 'Search' 'Music' 'Your Library'; do tapt "^$T\$" ; sleep 4; n=$(echo $T|tr -d ' '); snap tab_${n}_0; swipe_up; sleep 2; snap tab_${n}_1; swipe_up; sleep 2; snap tab_${n}_2; done
rec_start 05_search_play
tapt '^Search$'; sleep 3
c=$(python3 .github/demo/ui.py edit 0) && { tap $c; sleep 1; typeslow "kesariya"; sleep 1; adb shell input keyevent 66; }
sleep 7; snap s9_results
tapt 'Kesariya'; sleep 12; snap s10_player
sleep 10; snap s11_player2
rec_stop 05_search_play
adb logcat -d > dumps/logcat.txt; ls -la clips
