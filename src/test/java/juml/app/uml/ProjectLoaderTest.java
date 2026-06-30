// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Before;
import org.junit.Test;

import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JProgressBar;

import static org.junit.Assert.*;

public class ProjectLoaderTest {

    private JProgressBar progressBar;
    private JLabel statusLabel;
    private ProjectLoader loader;

    @Before
    public void setUp() {
        progressBar = new JProgressBar();
        statusLabel = new JLabel();
        ProjectLoaderDeps deps = new ProjectLoaderDeps();
        deps.cache = new ProjectAnalysisCache();
        deps.refIndexCache = new ReferenceIndexCache(new ProjectAnalysisCache());
        deps.state = new DiagramState();
        deps.treePanel = new ProjectTreePanel();
        deps.manifestSummaryPanel = new ManifestSummaryPanel();
        deps.loadProgress = progressBar;
        deps.cancelLoadingItem = new JMenuItem();
        deps.statusLabel = statusLabel;
        deps.parentFrame = null;
        deps.cancelTokenSetter = token -> { };
        deps.projectRootSetter = root -> { };
        deps.onLoadSuccess = root -> { };
        loader = new ProjectLoader(deps);
    }

    @Test
    public void updateLoadProgress_withTotal_setsDeterminate() {
        progressBar.setIndeterminate(true);
        loader.updateLoadProgress(3, 10, "parsing");
        assertFalse(progressBar.isIndeterminate());
        assertEquals(10, progressBar.getMaximum());
        assertEquals(3, progressBar.getValue());
    }

    @Test
    public void updateLoadProgress_withZeroTotal_setsIndeterminate() {
        progressBar.setIndeterminate(false);
        loader.updateLoadProgress(0, 0, "Scanning...");
        assertTrue(progressBar.isIndeterminate());
    }

    @Test
    public void updateLoadProgress_withMessage_updatesStatus() {
        loader.updateLoadProgress(5, 20, "MyClass.java");
        assertTrue(statusLabel.getText().contains("MyClass.java"));
    }

    @Test
    public void updateLoadProgress_doneExceedsTotal_clampsToTotal() {
        loader.updateLoadProgress(25, 10, null);
        assertEquals(10, progressBar.getValue());
    }
}
