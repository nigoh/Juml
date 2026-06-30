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
  # 例: openjdk version "17.0.10" → メジャー番号 17 を取り出す
  ver_raw="$(java -version 2>&1 | head -n1)"
  major="$(printf '%s\n' "$ver_raw" | sed -E 's/.*version "([0-9]+).*/\1/')"
  if [ -n "${major:-}" ] && [ "$major" -ge 17 ] 2>/dev/null; then
    java_line="Java: OK (${ver_raw})"
  else
    java_line="Java: バージョン不足の可能性 (${ver_raw}) — Juml は 17 以上が必要"
  fi
fi

# --- Juml jar の有無確認 -----------------------------------------------------
jar_line="Juml jar: 未ビルド → 図化スキルを使う前に './gradlew jar' を実行してください"
shopt -s nullglob 2>/dev/null
jars=("$repo_root"/build/libs/*.jar)
if [ "${#jars[@]}" -gt 0 ]; then
  jar_line="Juml jar: OK (${jars[0]})"
fi

# --- レポート出力（additionalContext） ---------------------------------------
cat <<EOF
[Juml SessionStart] 開発環境チェック
- ${java_line}
- ${jar_line}
- ステアリング: .claude/STEERING.md（仕組みの使い分け）/ 領域別ルールは .claude/rules/
EOF

exit 0
