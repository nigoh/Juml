// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.devtools;

import juml.util.ErrorCode;
import juml.util.Messages;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * エラーカタログ ({@link ErrorCode} + Messages) から {@code docs/errors.md} を生成する
 * 開発用ツール。enum をソースオブトゥルースとし、ドキュメントの手動更新による乖離を
 * 構造的に防ぐ。
 *
 * <p>実行: {@code gradle generateErrorDocs} (出力先は引数で指定、既定は
 * {@code docs/errors.md})。生成結果がリポジトリの docs/errors.md と一致することは
 * {@code ErrorCatalogDocTest} が CI で検証する。</p>
 */
public final class ErrorCatalogDoc {

    private ErrorCatalogDoc() {
    }

    /** docs/errors.md の全文を組み立てる (日英併記)。 */
    public static String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Juml エラーコード一覧 (Error Code Reference)\n\n");
        sb.append("<!-- このファイルは `gradle generateErrorDocs` により ");
        sb.append("`juml.util.ErrorCode` から自動生成されます。手で編集しないでください。 -->\n");
        sb.append("<!-- Generated from juml.util.ErrorCode by ");
        sb.append("`gradle generateErrorDocs`. Do not edit by hand. -->\n\n");
        sb.append("ログ・画面に表示されるエラー ID (例: `UML-R001`) から原因と対処法を引くための");
        sb.append("カタログです。アプリ内では「ヘルプ → エラーコード一覧」から同じ内容を参照できます。\n\n");
        sb.append("ID の形式は `領域プレフィックス + 3 桁連番`、ERROR / WARN の別は ID に含めず");
        sb.append("ログのレベル欄で表します。採番・追加のルールは ");
        sb.append("`.claude/rules/error-logging.md` を参照してください。\n");

        ErrorCode.Area lastArea = null;
        for (ErrorCode c : ErrorCode.values()) {
            if (!c.hasId()) {
                continue;
            }
            if (c.getArea() != lastArea) {
                lastArea = c.getArea();
                sb.append("\n## ").append(lastArea.getPrefix())
                  .append(" — ").append(ja("errcode.area." + lastArea.getPrefix()))
                  .append(" / ").append(en("errcode.area." + lastArea.getPrefix()))
                  .append("\n\n");
            }
            sb.append("### ").append(c.getId()).append("\n\n");
            sb.append("- **概要**: ").append(ja("errcode." + c.getId() + ".summary"))
              .append('\n');
            sb.append("- **対処**: ").append(ja("errcode." + c.getId() + ".remedy"))
              .append('\n');
            sb.append("- **Summary**: ").append(en("errcode." + c.getId() + ".summary"))
              .append('\n');
            sb.append("- **Remedy**: ").append(en("errcode." + c.getId() + ".remedy"))
              .append('\n');
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String ja(String key) {
        return localized(key, "ja");
    }

    private static String en(String key) {
        return localized(key, "en");
    }

    /**
     * 言語を切り替えてキーを引く。{@link Messages} はプロセス全体の言語状態を持つため、
     * 引いた後に元の言語へ戻す。
     */
    private static String localized(String key, String lang) {
        String prev = Messages.getLanguage();
        try {
            Messages.setLanguage(lang);
            return Messages.get(key);
        } finally {
            Messages.setLanguage(prev);
        }
    }

    /** 出力先 (省略時 docs/errors.md) へ生成結果を書き込む。 */
    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "docs/errors.md");
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        Files.write(out, render().getBytes(StandardCharsets.UTF_8));
        System.out.println("wrote " + out.toAbsolutePath());
    }
}
