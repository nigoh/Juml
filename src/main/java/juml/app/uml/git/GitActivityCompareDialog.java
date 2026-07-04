// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.PlantUmlActivityDiagram;

import java.awt.Window;
import java.util.List;

/**
 * メソッドの<b>アクティビティ図</b>(if/loop/return の制御フロー) を旧/新で左右に並べて
 * 比較するダイアログ。「関数の中身がどう変わったか」を図で見る。変化したアクションノードは
 * {@link ActivityDiffHighlight} で色付けする。
 */
final class GitActivityCompareDialog extends MethodDiagramCompareDialog {

    GitActivityCompareDialog(Window owner, GitRepoService svc, String relPath,
                             String oldRev, String newRev, String newLabel) {
        super(owner, svc, relPath, oldRev, newRev, "git.actcmp.title");
    }

    @Override protected String generateExisting(List<JavaClassInfo> classes,
                                                String className, String methodName) {
        PlantUmlActivityDiagram.Options opt = new PlantUmlActivityDiagram.Options();
        opt.includeLegend = false;
        return PlantUmlActivityDiagram.generate(classes, className, methodName, opt);
    }

    @Override protected String[] colorizeDiagram(String oldPuml, String newPuml) {
        return ActivityDiffHighlight.colorize(oldPuml, newPuml);
    }
}
