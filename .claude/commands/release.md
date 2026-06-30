# /release

Juml の新バージョンをリリースする。

## 使用方法

```
/release <version>
```

例: `/release 2.2`

---

## 実行手順

以下のステップを順番に実行する。エラーが発生した場合は必ずユーザーに報告してから止まること。

### Step 1: バージョン確認

`$ARGUMENTS` に指定されたバージョン番号 (例: `2.2`) を `VERSION` として使う。引数がなければユーザーに確認する。

### Step 2: 事前チェック

```bash
git status
git log --oneline -3
```

未コミットの変更があれば警告する。

### Step 3: `build.gradle` のバージョンを更新

`build.gradle` の `version = '...'` を `version = 'VERSION'` に書き換える。

### Step 4: `CHANGE.md` の Unreleased セクションをバージョン名に変更

`CHANGE.md` の冒頭にある

```
Unreleased
--------
```

を

```
VERSION
--------
```

に書き換える。変更点が "Unreleased" セクションに記載されていない場合はユーザーに確認する。

### Step 5: fat jar のビルド

```bash
./gradlew jar
```

ビルド成功を確認する。失敗したらユーザーに報告して停止する。

### Step 6: 変更をコミット

```bash
git add build.gradle CHANGE.md
git commit -m "Release v<VERSION>"
git push -u origin <current-branch>
```

### Step 7: git タグを作成してプッシュ

```bash
git tag v<VERSION>
git push origin v<VERSION>
```

### Step 8: GitHub リリースを作成

タグ `v<VERSION>` から GitHub Release を作成する。リリースノートには `CHANGE.md` の対応バージョンセクションの内容を使う。
jar ファイル (`build/libs/Juml.jar`) をリリースアセットとして添付する方法をユーザーに案内する（GitHub MCP に create_release がない場合は手順を説明する）。

### Step 9: PR 作成（ブランチ作業の場合）

feature ブランチで作業している場合は draft PR を作成する。

---

## 注意事項

- リリース前に `./gradlew test` でテストが全通することを確認することを推奨する
- `CHANGE.md` の新バージョンセクションに変更点が記載されていること
- GitHub Release の jar アセット添付は `gh release upload v<VERSION> build/libs/Juml.jar` で可能（`gh` CLI がある環境のみ）
