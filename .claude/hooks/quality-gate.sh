#!/usr/bin/env bash
# Stop hook — Java 変更の品質ゲートを決定論的に回す（検証ループの自動化）。
#
# 目的: 記事「Getting started with loops」の自己検証ループを決定論化する。
#       .java / build.gradle / checkstyle 設定に未検証の変更がある状態で
#       ターンを終えようとしたら、コンパイル + checkstyle（CI の check の
#       静的部分に相当）を実行し、失敗していれば exit 2 で停止をブロックして
#       Claude に修正を続けさせる。プロンプト指示（「毎回検証して」）では
#       破られうるため Hook で強制する（記事「Steering Claude Code」）。
#
# 仕様:
#   - stdin に Stop イベントの JSON。stop_hook_active=true（この hook 起因の
#     継続中）の場合は再ブロックしない＝修正ループは 1 停止につき 1 回まで。
#     無限ループを避けつつ「失敗したまま黙って終わる」ことを防ぐ。
#   - 対象パスに変更が無ければ即 exit 0（コスト最小化）。
#   - 同一の変更状態で一度合格していれば再実行しない（ハッシュを
#     .git/juml-quality-gate.pass に記録）。
#   - テスト実行は含めない（遅い）。フル検証は /juml-verify スキルで行う。
set -u

repo_root="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$repo_root" || exit 0

# jq が無ければフェイルオープン（判定不能のままブロックしない）
if command -v jq >/dev/null 2>&1; then
  input="$(cat)"
  stop_active="$(printf '%s' "$input" | jq -r '.stop_hook_active // false' 2>/dev/null)"
else
  cat >/dev/null 2>&1
  exit 0
fi

# git リポジトリでなければ対象外
git rev-parse --git-dir >/dev/null 2>&1 || exit 0

# ゲート対象: Java ソース / ビルドスクリプト / checkstyle 設定
paths=(':(glob)**/*.java' 'build.gradle' 'settings.gradle' ':(glob)config/checkstyle/**')

changed="$(git status --porcelain -- "${paths[@]}" 2>/dev/null)"
[ -z "$changed" ] && exit 0

# 変更状態のハッシュ（tracked の diff + 状態一覧 + untracked .java の内容）
state="$(
  {
    git diff HEAD -- "${paths[@]}" 2>/dev/null
    printf '%s\n' "$changed"
    git ls-files --others --exclude-standard -- '*.java' 2>/dev/null \
      | while IFS= read -r f; do sha1sum "$f" 2>/dev/null; done
  } | sha1sum | cut -d' ' -f1
)"

stamp="$repo_root/.git/juml-quality-gate.pass"
if [ -f "$stamp" ] && grep -qx "$state" "$stamp" 2>/dev/null; then
  exit 0  # この変更状態は既に合格済み
fi

# Gradle 起動コマンドの選定: wrapper の配布物が展開済みなら ./gradlew、
# 未展開（ネットワーク制限でダウンロード不可の環境など）ならシステム gradle に
# フォールバックする。どちらも無ければ判定不能なのでフェイルオープン。
pick_gradle() {
  local dist_ver
  dist_ver="$(sed -n 's/.*gradle-\(.*\)-bin\.zip.*/\1/p' \
    gradle/wrapper/gradle-wrapper.properties 2>/dev/null)"
  if [ -n "$dist_ver" ] \
     && ls "$HOME/.gradle/wrapper/dists/gradle-${dist_ver}-bin"/*/"gradle-${dist_ver}/bin/gradle" \
        >/dev/null 2>&1; then
    echo "./gradlew"
  elif command -v gradle >/dev/null 2>&1; then
    echo "gradle"
  elif [ -x ./gradlew ]; then
    echo "./gradlew"  # 最後の手段: wrapper にダウンロードを試みさせる
  else
    echo ""
  fi
}

gradle_cmd="$(pick_gradle)"
[ -z "$gradle_cmd" ] && exit 0

# この hook 起因の継続中に再度失敗しても、無限ループ回避のためブロックはせず警告のみ
run_gate() {
  # "Picked up JAVA_TOOL_OPTIONS" は環境ノイズなのでフィードバックから除く
  "$gradle_cmd" -q compileJava compileTestJava checkstyleMain checkstyleTest 2>&1 \
    | grep -v '^Picked up JAVA_TOOL_OPTIONS'
  return "${PIPESTATUS[0]}"
}

gate_out="$(run_gate)"
gate_rc=$?

if [ "$gate_rc" -eq 0 ]; then
  printf '%s\n' "$state" > "$stamp"
  exit 0
fi

# 失敗: 末尾（エラー本文が出る部分）だけを返してコンテキストを節約
tail_out="$(printf '%s\n' "$gate_out" | tail -n 40)"
if [ "$stop_active" = "true" ]; then
  # 既にこの hook で継続させた後も落ちている → 無限ループを避けて通すが、
  # 未解決であることを明示的に残す。
  echo "[quality-gate] 品質ゲートが未解決のままです（compile/checkstyle 失敗）。" >&2
  echo "$tail_out" >&2
  exit 0
fi

echo "[quality-gate] compile または checkstyle が失敗しています。修正してから終了してください。" >&2
echo "実行: $gradle_cmd -q compileJava compileTestJava checkstyleMain checkstyleTest" >&2
echo "--- 失敗ログ（末尾40行） ---" >&2
echo "$tail_out" >&2
exit 2
