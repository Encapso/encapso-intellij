package io.github.encapso.intellij.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import io.github.encapso.intellij.service.EncapsoBoundaryService;
import org.jetbrains.annotations.NotNull;

/**
 * Intercepts Code Completion variants to enforce Encapso's architectural visibility.
 * 
 * High Fidelity: Filtered Delegation.
 */
public class EncapsoCompletionContributor extends CompletionContributor {

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        EncapsoBoundaryService service = EncapsoBoundaryService.getInstance(parameters.getEditor().getProject());
        PsiElement position = parameters.getPosition();

        // RUN remaining contributors (like Java) and filter their results before they reach the UI
        result.runRemainingContributors(parameters, completionResult -> {
            LookupElement element = completionResult.getLookupElement();
            Object obj = element.getObject();
            
            if (obj instanceof PsiClass pc) {
                // Architectural Boundary Veto
                if (!service.isAccessible(pc, position)) {
                    return; // VETO
                }
            } else {
                // Secondary check for patterns (e.g. from String-based indices)
                String name = element.getLookupString();
                if (name.endsWith("Impl")) {
                     if (service.checkViolation(parameters.getOriginalFile(), position).isPresent()) {
                         return; // Veto
                     }
                }
            }

            // Only pass valid, architecturally-compliant results
            result.passResult(completionResult);
        });
    }
}
