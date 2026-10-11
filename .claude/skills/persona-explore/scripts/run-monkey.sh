#!/usr/bin/env bash
# GuiMonkey (探索ハーネス) を Xvfb 上で 1 回走らせ、JSON レポートとスクリーンショットを残す。
#
# usage: run-monkey.sh <runName> <projectDir> [options...]
#   options は GuiMonkey へそのまま渡す: --alt <dir> --scenarios S2,S3 --seed 7 --fuzz 80 --persona newcomer
#   出力: $JUML_MONKEY_OUT/<runName>/{report.json,stdout.log,stderr.log,shots/}
#   環境変数: JUML_MONKEY_OUT (既定 /tmp/juml-monkey) / JUML_MONKEY_TIMEOUT 秒 (既定 1500)
#
# 前提: gradle jar と gradle compileTestJava 済み (無ければここでビルドする)。
set -u
NAME="${1:?runName}"; PROJ="${2:?projectDir}"; shift 2
ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
OUT_BASE="${JUML_MONKEY_OUT:-/tmp/juml-monkey}"
RUN="$OUT_BASE/$NAME"
JAR="$ROOT/build/libs/Juml.jar"
CLASSES="$ROOT/build/classes/java/test"
TIMEOUT_S="${JUML_MONKEY_TIMEOUT:-1500}"

if [ ! -f "$JAR" ] || [ ! -f "$CLASSES/juml/app/uml/GuiMonkey.class" ]; then
  echo "[run-monkey] building jar + test classes ..."
  (cd "$ROOT" && gradle -q jar compileTestJava) || { echo "[run-monkey] BUILD FAILED"; exit 2; }
fi

rm -rf "$RUN"; mkdir -p "$RUN/shots" "$RUN/home"
# 付箋ストア (<project>/.juml) を走行前に持っていなければ、走行後に消して入力プロジェクトを汚さない。
HAD_JUML=0; [ -e "$PROJ/.juml" ] && HAD_JUML=1
export HOME="$RUN/home"
timeout "$TIMEOUT_S" xvfb-run -a -s "-screen 0 1600x1000x24" \
  java -Duser.home="$RUN/home" -Djava.awt.headless=false \
       -cp "$JAR:$CLASSES" juml.app.uml.GuiMonkey \
       --project "$PROJ" --out "$RUN/report.json" --shots "$RUN/shots" "$@" \
  > "$RUN/stdout.log" 2> "$RUN/stderr.log"
EXIT=$?
[ "$HAD_JUML" = 0 ] && rm -rf "$PROJ/.juml"
echo "exit=$EXIT run=$RUN"
echo "--- findings summary (kind | count) ---"
if [ -f "$RUN/report.json" ]; then
  jq -r '.findings[] | .kind' "$RUN/report.json" | sort | uniq -c | sort -rn
  echo "--- findings (first line, deduped) ---"
  jq -r '.findings[] | "\(.scenario) | \(.kind) | \(.message | split("\n")[0])"' "$RUN/report.json" \
    | cut -c1-200 | sort | uniq -c | sort -rn | head -80
  echo "--- run ---"
  jq -r '"persona=\(.persona) seed=\(.seed) actions=\(.actions) elapsedMs=\(.elapsedMs) scenarios=\(.scenarios|join(","))"' "$RUN/report.json"
  echo "shots: $(ls "$RUN/shots" 2>/dev/null | wc -l) files in $RUN/shots"
else
  echo "(no report.json — harness died before writing it)"; tail -20 "$RUN/stderr.log"
fi
