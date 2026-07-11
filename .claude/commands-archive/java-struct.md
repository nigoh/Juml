---
description: 指定した Java ファイルの構造抽出（JavaStructureExtractor）結果を確認し、クラス/メソッド/フィールドが正しく解析されているかを検証する
allowed-tools: Read, Bash
---

## タスク

対象ファイル: `$ARGUMENTS`

`$ARGUMENTS` が空の場合は `src/main/java/juml/core/formats/uml/JavaStructureExtractor.java` を対象にする。

### 手順

1. **対象ファイルを Read で確認する**

2. **`JavaStructureExtractor` の抽出結果として期待される構造を推定する**

   抽出されるデータモデル:

   | 項目 | クラス | 概要 |
   |---|---|---|
   | クラス宣言 | `JavaClassInfo` | `kind` (CLASS/INTERFACE/ENUM/ANNOTATION), `modifiers`, `superClass`, `interfaces` |
   | フィールド | `JavaFieldInfo` | `name`, `type`, `modifiers`, `annotations` |
   | メソッド | `JavaMethodInfo` | `name`, `returnType`, `params`, `statements` |
   | 呼び出し | `JavaMethodInfo.Call` | `receiver`, `methodName` |
   | 制御ブロック | `JavaMethodInfo.Block` | `kind` (IF/WHILE/FOR/TRY 等), `branches` |

3. **以下の観点で解析結果を検証する**

   - **内部クラス**: `classStack` によるネスト処理が正しく機能するか
   - **ジェネリクス**: `readTypeName()` がダイアモンド演算子やワイルドカードを正しく読むか
   - **アノテーション**: `@` で始まるアノテーションと `@interface` を誤認しないか
   - **enum**: `enumConstants` の抽出と通常フィールドの区別
   - **Stage A / Stage B**: `extractHeadersOnly` 時は fields/methods/comment が空になるか
   - **シーケンス図用呼び出し**: `getCalls()` に `receiver.method()` 形式で収録されるか

4. **検証結果を日本語で報告する**

```
## 構造抽出レポート: <ファイル名>

### 期待される JavaClassInfo 構造
```
package: <パッケージ>
classes:
  - <ClassName> (<kind>)
    superClass: <型>
    interfaces: [<型>, ...]
    fields: [<name>: <type>, ...]
    methods: [<name>(<params>): <return>, ...]
```

### 抽出上の注意点
- <項目>: <説明>

### 潜在的な誤抽出リスク
- <リスクタイトル>: <説明>
  対策: <推奨する対処>

### 改善提案
- <提案タイトル>: <内容>
```
