// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.PlantUmlSequenceDiagram;

import java.awt.Window;
import java.util.List;

/**
 * メソッドの<b>シーケンス図</b>(呼び出しトレース) を旧/新で左右に並べて比較するダイアログ。
 * 「関数がどの順にどのメソッドを呼ぶか」の変化を図で見る。変化したメッセージは
 * {@link SequenceDiffHighlight} でラベルを色付けする。
 *
 * <p>呼び出し先が別ファイルのクラスにある場合、同梱の単一ファイル解析では展開に限界が
 * あるが、対象クラス内のメソッド呼び出し列とその増減は追える。</p>
 */
final class GitSequenceCompareDialog extends MethodDiagramCompareDialog {

    GitSequenceCompareDialog(Window owner, GitRepoService svc, String relPath,
                             String oldRev, String newRev, String newLabel) {
        super(owner, svc, relPath, oldRev, newRev, "git.seqcmp.title");
    }

    @Override protected String generateExisting(List<JavaClassInfo> classes,
                                                String className, String methodName) {
        PlantUmlSequenceDiagram.Options opt = new PlantUmlSequenceDiagram.Options();
        opt.includeLegend = false;
        return PlantUmlSequenceDiagram.generate(classes, className, methodName, opt);
    }

    @Override protected String[] colorizeDiagram(String oldPuml, String newPuml) {
        return SequenceDiffHighlight.colorize(oldPuml, newPuml);
    }
}
