# /aaos-help

AAOS × Juml の知識・リファレンス。自動車向けアーキテクチャのクイックアクセス。

## 説明

AAOS トピック別のクイックリファレンス。
質問に答えるだけで、関連するチートシートや解析フロー情報を提供します。

## 使用方法

```
/aaos-help <topic> [query]
```

### Topic リスト

| Topic | 説明 | Query 例 |
|---|---|---|
| `carservice` | CarService / CarManager / ICar AIDL | `"CarPropertyManager のアーキテクチャ"` |
| `vhal` | Vehicle HAL / IVehicle / subscribe | `"VHAL の get/set フロー"` |
| `ui` | CarSystemUI / CarLauncher / RRO | `"RRO でのテーマカスタマイズ"` |
| `security` | MultiUser / permissions / sepolicy | `"driver と passenger のロール分離"` |
| `all` | 全トピック リスト表示 | - |

## 例

```
# CarService について知りたい
/aaos-help carservice

# CarPropertyManager の詳細
/aaos-help carservice "CarPropertyManager.getProperty の流れ"

# VHAL subscribe パターン
/aaos-help vhal "subscribe で値変更を受け取る方法"

# CarSystemUI RRO カスタマイズ
/aaos-help ui "RRO でリソースを置き換える仕組み"

# MultiUser の権限分離
/aaos-help security "passenger は何ができない？"

# 全トピック一覧
/aaos-help all
```

## 動作

1. Topic とクエリを受け取る
2. 該当チートシート (`.claude/skills/aaos-juml-analyzer/cheatsheet-*.md`) をロード
3. 関連セクションを抽出・説明
4. 必要に応じて Juml コマンド例・Grep 例を提示

## 関連 Juml CLI

- `--vhal-flow`: CarPropertyManager の get/set/subscribe フローを Markdown + PlantUML 図で可視化
- `--aidl-binding`: プロジェクト内 AIDL インタフェースと、その `Stub` を継承する実装クラスとの対応表 (Markdown)
- `--impact <FQN[.method]>`: 「このシンボルを消すと何が壊れるか」逆参照 + 推移閉包レポート
- `--ref-find <FQN[.member]>`: シンボルへの参照箇所を grep 互換で列挙

## AOSP 知識も必要？

AAOS は AOSP ベースなので、Soong / partition / HAL / sepolicy は共通です。
それらについては `/aosp-help` も併せて参照してください。

---

**Note**: スキル `aaos-juml-analyzer` が自動ロードされます。
「AAOS アーキテクチャを理解したい」というときに活用。
