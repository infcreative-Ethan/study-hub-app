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
# GitHub 原始文件的 SHA-256（来自 API），用来校验下载结果，所以走镜像也安全
SHA=$(echo "$API" | grep -B6 "browser_download_url\": *\"[^\"]*$APK\"" | grep -o 'sha256:[0-9a-f]*' | tail -1 | cut -d: -f2)
# 国内服务器直连 GitHub 很慢：依次尝试直连和几个公共加速镜像
ok=0
for PREFIX in "" "https://ghfast.top/" "https://gh-proxy.com/" "https://ghproxy.net/"; do
  rm -f "$DIR/$APK.part"
  if curl -fsSL --retry 2 --connect-timeout 15 --speed-limit 20000 --speed-time 30 --max-time 900 \
       "$PREFIX$URL_APK" -o "$DIR/$APK.part"; then
    GOT=$(sha256sum "$DIR/$APK.part" | cut -d' ' -f1)
    if [ -z "$SHA" ] || [ "$GOT" = "$SHA" ]; then
      unzip -tq "$DIR/$APK.part" >/dev/null 2>&1 && { ok=1; echo "downloaded via ${PREFIX:-github}"; break; }
    fi
    echo "checksum mismatch via ${PREFIX:-github}"
  fi
done
if [ "$ok" != 1 ]; then rm -f "$DIR/$APK.part"; echo "download failed, will retry next time"; exit 1; fi
mv "$DIR/$APK.part" "$DIR/$APK"
cp /tmp/latest.json "$DIR/latest.json"
ls -1t "$DIR"/StudyHub-*.apk 2>/dev/null | tail -n +4 | xargs -r rm -f   # 只保留最近 3 个版本
chmod -R a+rX "$DIR"
echo "synced $APK"
