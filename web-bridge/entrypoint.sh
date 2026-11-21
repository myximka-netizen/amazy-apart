#!/bin/sh
set -eu
umask 077
export DISPLAY=:99
mkdir -p /data
Xvfb :99 -screen 0 1440x900x24 -nolisten tcp &
for i in $(seq 1 50); do xdpyinfo >/dev/null 2>&1 && break; sleep .2; done
xdpyinfo >/dev/null
x11vnc -storepasswd "$WEB_BRIDGE_VNC_PASSWORD" /data/vnc.pass >/dev/null 2>&1
x11vnc -display :99 -localhost -rfbauth /data/vnc.pass -forever -shared -rfbport 5900 -quiet >/dev/null 2>&1 &
websockify --web=/usr/share/novnc/ 6080 localhost:5900 >/dev/null 2>&1 &
exec python -m uvicorn bridge:app --host 0.0.0.0 --port 8080 --no-access-log
