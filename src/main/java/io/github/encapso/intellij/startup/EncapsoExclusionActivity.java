package io.github.encapso.intellij.startup;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.concurrency.AppExecutorUtil;
import io.github.encapso.intellij.core.EncapsoConstants;
import io.github.encapso.intellij.service.EncapsoBoundaryService;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Programmatically excludes Encapso implementation classes to prevent AI-driven 
 * ghost text from suggesting them.
 * 
 * Delegates discovery to EncapsoBoundaryService to follow Clean Architecture principles.
 */
public class EncapsoExclusionActivity implements ProjectActivity {
    private static final Logger LOG = Logger.getInstance(EncapsoExclusionActivity.class);

    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        try {
            DumbService.getInstance(project).runWhenSmart(() -> {
                updateExclusions(project);
            });
        } catch (Exception e) {
            LOG.error("Failed to initialize Encapso exclusions", e);
        }
        return Unit.INSTANCE;
    }

    private void updateExclusions(@NotNull Project project) {
        EncapsoBoundaryService service = EncapsoBoundaryService.getInstance(project);

        ReadAction.nonBlocking(() -> {
            List<String> fqnsToExclude = new ArrayList<>();
            try {
                PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);
                GlobalSearchScope scope = GlobalSearchScope.projectScope(project);

                String[] names = cache.getAllClassNames();
                for (String name : names) {
                    if (name != null && name.endsWith(EncapsoConstants.IMPL_SUFFIX)) {
                        for (PsiClass psiClass : cache.getClassesByName(name, scope)) {
                            String fqn = psiClass.getQualifiedName();
                            if (fqn != null) {
                                fqnsToExclude.add(fqn);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOG.warn("Error during Encapso exclusion analysis", e);
            }
            return fqnsToExclude;
        }).finishOnUiThread(com.intellij.openapi.application.ModalityState.defaultModalityState(), fqns -> {
            try {
                applyExclusions(fqns.stream().collect(Collectors.toSet()));
            } catch (Exception e) {
                LOG.error("Failed to apply Encapso exclusions to IDE settings", e);
            }
        }).submit(AppExecutorUtil.getAppExecutorService());
    }

    private void applyExclusions(Set<String> fqns) {
        CodeInsightSettings settings = CodeInsightSettings.getInstance();
        if (settings == null) return;

        String[] currentArr = settings.EXCLUDED_PACKAGES;
        if (currentArr == null) return;
        
        List<String> currentExclusions = new ArrayList<>(Arrays.asList(currentArr));
        boolean changed = false;

        // Clean up stale "package-level" exclusions
        List<String> toRemove = currentExclusions.stream()
                .filter(e -> !e.endsWith(EncapsoConstants.IMPL_SUFFIX) && 
                            (e.contains("component") || e.contains("encapso")))
                .collect(Collectors.toList());
        
        if (!toRemove.isEmpty()) {
            currentExclusions.removeAll(toRemove);
            changed = true;
        }

        // Add current Impl classes
        for (String fqn : fqns) {
            if (fqn != null && !currentExclusions.contains(fqn)) {
                currentExclusions.add(fqn);
                changed = true;
            }
        }

        if (changed) {
            settings.EXCLUDED_PACKAGES = currentExclusions.toArray(new String[0]);
        }
    }
}
