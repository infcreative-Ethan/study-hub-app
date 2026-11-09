#!/bin/bash
# 服务器定时任务：把 GitHub 上最新编译的 APK 同步到资源服务器的 app/ 目录，
# APP 会优先从这里检查和下载更新（国内下载更快）。
# 安装：sudo cp sync_apk.sh /usr/local/bin/ && sudo chmod +x /usr/local/bin/sync_apk.sh
#       (sudo crontab -l 2>/dev/null; echo "*/10 * * * * /usr/local/bin/sync_apk.sh >/dev/null 2>&1") | sudo crontab -
set -e
DIR=/var/www/studyhub/res/app
REPO=infcreative-Ethan/study-hub-app
mkdir -p "$DIR"
API=$(curl -fsSL --max-time 30 "https://api.github.com/repos/$REPO/releases/latest") || exit 0
URL_JSON=$(echo "$API" | grep -o '"browser_download_url": *"[^"]*latest.json"' | sed 's/.*"\(http[^"]*\)"/\1/')
[ -z "$URL_JSON" ] && exit 0
curl -fsSL --max-time 60 "$URL_JSON" -o /tmp/latest.json || exit 0
NEW=$(grep -o '"versionCode": *[0-9]*' /tmp/latest.json | grep -o '[0-9]*$')
OLD=$(grep -o '"versionCode": *[0-9]*' "$DIR/latest.json" 2>/dev/null | grep -o '[0-9]*$' || echo 0)
[ "$NEW" -le "${OLD:-0}" ] && exit 0
APK=$(grep -o '"apk": *"[^"]*"' /tmp/latest.json | sed 's/.*"\([^"]*\)"$/\1/')
URL_APK=$(echo "$API" | grep -o "\"browser_download_url\": *\"[^\"]*$APK\"" | sed 's/.*"\(http[^"]*\)"/\1/')
# 服务器连 GitHub 不稳定：断点续传，最多重试 8 次；下载不完整就不更新 latest.json
ok=0
for i in 1 2 3 4 5 6 7 8; do
  if curl -fsSL --retry 3 --max-time 900 -C - "$URL_APK" -o "$DIR/$APK.part"; then ok=1; break; fi
  sleep 5
done
SIZE=$(stat -c %s "$DIR/$APK.part" 2>/dev/null || echo 0)
if [ "$ok" != 1 ] || [ "$SIZE" -lt 1000000 ] || ! unzip -tq "$DIR/$APK.part" >/dev/null 2>&1; then
  echo "download failed, will retry next time"; exit 1
fi
mv "$DIR/$APK.part" "$DIR/$APK"
cp /tmp/latest.json "$DIR/latest.json"
ls -1t "$DIR"/StudyHub-*.apk 2>/dev/null | tail -n +4 | xargs -r rm -f   # 只保留最近 3 个版本
chmod -R a+rX "$DIR"
echo "synced $APK"
