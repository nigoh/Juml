#!/usr/bin/env bash
# PreToolUse(Bash) hook — 危険な git push を決定論的にブロックする。
#
# 目的: 本リポジトリは「指定された作業ブランチ以外へ push しない」運用。
#       プロンプト指示だけでは破られうるため（記事「絶対に X するな → Hook」）、
#       保護ブランチ(main/master)宛ての push と force push を確実に止める。
#
# 仕様: stdin に PreToolUse の JSON（tool_input.command）。
#       ブロック時は理由を stderr に出して exit 2、許可時は exit 0。
#       誤検知を避けるため、文字列中に "git push" を含むだけのコマンド
#       （例: echo "..." やコメント）は対象にせず、行頭またはシェル区切り
#       (; & | && ||) の直後に現れる実際の git push 呼び出しのみを判定する。
set -u

# stdin の JSON からコマンド文字列を取得（jq が無ければ素通り＝フェイルオープン）
if command -v jq >/dev/null 2>&1; then
  cmd="$(jq -r '.tool_input.command // ""' 2>/dev/null)"
else
  cat >/dev/null 2>&1
  exit 0
fi

# 実際の git push 呼び出し（行頭 or 区切り直後）でなければ対象外
if ! printf '%s' "$cmd" | grep -Eq '(^|[;&|])[[:space:]]*git[[:space:]]+push([[:space:]]|$)'; then
  exit 0
fi

block() {
  echo "[guard-git-push] ブロックしました: $1" >&2
  echo "理由: $2" >&2
  echo "対象コマンド: $cmd" >&2
  exit 2
}

# 1) force push を拒否
if printf '%s' "$cmd" | grep -Eq '[[:space:]](--force|--force-with-lease|-f)([[:space:]]|$)'; then
  block "force push は禁止" "履歴を破壊しうるため。通常の push を使ってください。"
fi
# refspec 先頭の '+'（強制）も拒否（例: git push origin +HEAD:main）
if printf '%s' "$cmd" | grep -Eq 'git[[:space:]]+push[^;&|]*[[:space:]]\+[^[:space:]]'; then
  block "force refspec(+) は禁止" "履歴を破壊しうるため。"
fi

# 2) 保護ブランチ(main/master)宛ての push を拒否
if printf '%s' "$cmd" | grep -Eq '(^|[[:space:]/:])(main|master)([[:space:]]|$|:)'; then
  block "保護ブランチ(main/master)への push は禁止" \
        "作業ブランチ(claude/...)へ push し、PR 経由でマージしてください。"
fi

exit 0
