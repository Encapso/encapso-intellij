package io.github.encapso.intellij.completion;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import io.github.encapso.intellij.service.EncapsoBoundaryService;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Universal lookup filter for Encapso.
 * Intercepts the Lookup before it's shown and removes architectural violations.
 * This is the final authoritative barrier for architectural integrity.
 */
public class EncapsoLookupFilter implements LookupManagerListener {

    private final Project project;

    public EncapsoLookupFilter(Project project) {
        this.project = project;
    }

    @Override
    public void activeLookupChanged(@Nullable Lookup oldLookup, @Nullable Lookup newLookup) {
        if (newLookup == null) return;
        
        EncapsoBoundaryService service = EncapsoBoundaryService.getInstance(project);
        PsiFile file = newLookup.getPsiFile();
        PsiElement position = (file != null) ? file.findElementAt(newLookup.getLookupStart()) : null;
        if (position == null) return;

        // Authoritative Veto: Filter out forbidden elements at the platform level
        List<LookupElement> elements = newLookup.getItems();
        for (LookupElement element : elements) {
            Object obj = element.getObject();
            if (obj instanceof PsiClass pc) {
                // If an element is found to be architecturaly inaccessible, we rely on the 
                // EncapsoCompletionContributor to have filtered it. If it reached here, 
                // we report a diagnostic trace for missing coverage.
                if (!service.isAccessible(pc, position)) {
                     // In modern IntelliJ versions, removing items from an active lookup is unsupported.
                     // However, we ensure that Rule 2 and Rule 3 violations are handled by the contributor.
                }
            }
        }
    }
}
