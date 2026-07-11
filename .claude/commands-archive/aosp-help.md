# /aosp-help

AOSP × Juml の知識・リファレンス。エージェント・スキル内容のクイックアクセス。

## 説明

AOSP トピック別のクイックリファレンス。
質問に答えるだけで、関連するチートシートや解析フロー情報を提供します。

## 使用方法

```
/aosp-help <topic> [query]
```

### Topic リスト

| Topic | 説明 | Query 例 |
|---|---|---|
| `build` | Soong / Android.bp / Gradle | `"What is Android.bp?"` |
| `partition` | system / vendor / product / odm | `"Treble partition 分離"` |
| `hal` | AIDL / HAL / HIDL / VINTF | `"HIDL から AIDL への移行"` |
| `sepolicy` | SELinux / sepolicy / avc denied | `"avc denied ログの読み方"` |
| `all` | 全トピック リスト表示 | - |

## 例

```
# Soong について知りたい
/aosp-help build

# partition について具体的に質問
/aosp-help partition "system と vendor の違いは？"

# HAL AIDL について
/aosp-help hal "AIDL インタフェースの定義方法"

# sepolicy デバッグ
/aosp-help sepolicy "avc denied が出たときはどうする？"

# 全トピック一覧
/aosp-help all
```

## 動作

1. Topic とクエリを受け取る
2. 該当チートシート (`.claude/skills/aosp-juml-analyzer/cheatsheet-*.md`) をロード
3. 関連セクションを抽出・説明
4. 必要に応じて Juml コマンド例を提示

---

**Note**: スキル `aosp-juml-analyzer` が自動ロードされます。
「仕組みを理解したい」「リファレンスが欲しい」というときに活用。
