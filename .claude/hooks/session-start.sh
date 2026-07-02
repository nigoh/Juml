#!/usr/bin/env bash
# SessionStart hook — Juml 開発環境の準備状況を確認してコンテキストへ渡す。
#
# 目的: リモート/エフェメラル環境はセッションごとにまっさらにクローンされ、
#       AOSP/AAOS スキルは build/libs の Juml jar を前提にする。起動時に
#       Java バージョンと jar の有無を確認し、無ければビルド方法を案内する
#       （自動ビルドはしない＝起動を速く保つ）。
#
# 仕様: stdin に hook イベントの JSON。常に exit 0。stdout は additionalContext
#       としてメインコンテキストに渡る（決定論的な環境レポート）。
set -u

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# --- Java バージョン確認 -----------------------------------------------------
java_line="Java: 見つかりません（JDK 17+ を用意してください）"
if command -v java >/dev/null 2>&1; then
  # 例: openjdk version "17.0.10" → メジャー番号 17 を取り出す。
  # 注意: JAVA_TOOL_OPTIONS が設定された環境では "Picked up JAVA_TOOL_OPTIONS: ..."
  # が 1 行目に混ざるため、head -n1 ではなく version 行を明示的に拾う。
  ver_raw="$(java -version 2>&1 | grep -m1 -E 'version "[0-9]')"
  major="$(printf '%s\n' "$ver_raw" | sed -E 's/.*version "([0-9]+).*/\1/')"
  if [ -n "${major:-}" ] && [ "$major" -ge 17 ] 2>/dev/null; then
    java_line="Java: OK (${ver_raw})"
  else
    java_line="Java: バージョン不足の可能性 (${ver_raw}) — Juml は 17 以上が必要"
  fi
fi

# --- Gradle 起動手段の確認 ----------------------------------------------------
# wrapper の配布物が展開済みか（ネットワーク制限環境ではダウンロードに失敗し
# .part のまま残る）を確認し、使えない場合はシステム gradle を案内する。
gradle_cmd="./gradlew"
dist_ver="$(sed -n 's/.*gradle-\(.*\)-bin\.zip.*/\1/p' \
  "$repo_root/gradle/wrapper/gradle-wrapper.properties" 2>/dev/null)"
if [ -n "$dist_ver" ] \
   && ls "$HOME/.gradle/wrapper/dists/gradle-${dist_ver}-bin"/*/"gradle-${dist_ver}/bin/gradle" \
      >/dev/null 2>&1; then
  gradle_line="Gradle: ./gradlew 利用可（wrapper ${dist_ver} 展開済み）"
elif command -v gradle >/dev/null 2>&1; then
  sys_ver="$(gradle --version 2>/dev/null | sed -n 's/^Gradle \(.*\)$/\1/p' | head -n1)"
  gradle_cmd="gradle"
  gradle_line="Gradle: wrapper ${dist_ver:-?} が未展開（DL 不可の可能性）→ この環境ではシステム gradle ${sys_ver:-?} を使ってください（./gradlew の代わりに gradle）"
else
  gradle_line="Gradle: wrapper 未展開かつシステム gradle 無し → ビルド不可の環境です"
fi

# --- Juml jar の有無・鮮度確認 -----------------------------------------------
jar_line="Juml jar: 未ビルド → 図化スキルを使う前に '${gradle_cmd} jar' を実行してください"
shopt -s nullglob 2>/dev/null
jars=("$repo_root"/build/libs/*.jar)
if [ "${#jars[@]}" -gt 0 ]; then
  # jar より新しいソースがあれば「古い jar」を掴んだまま検証しないよう再ビルドを促す。
  stale_src="$(find "$repo_root/src/main/java" -name '*.java' -newer "${jars[0]}" -print -quit 2>/dev/null)"
  if [ -n "$stale_src" ]; then
    jar_line="Juml jar: 存在するが古い（src が jar より新しい）→ '${gradle_cmd} jar' で再ビルド推奨"
  else
    jar_line="Juml jar: OK (${jars[0]})"
  fi
fi

# --- レポート出力（additionalContext） ---------------------------------------
cat <<EOF
[Juml SessionStart] 開発環境チェック
- ${java_line}
- ${gradle_line}
- ${jar_line}
- ステアリング: .claude/STEERING.md（仕組みの使い分け）/ 領域別ルールは .claude/rules/
EOF

exit 0
