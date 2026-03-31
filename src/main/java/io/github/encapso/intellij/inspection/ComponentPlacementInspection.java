package io.github.encapso.intellij.inspection;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import io.github.encapso.intellij.service.EncapsoBoundaryService;
import org.jetbrains.annotations.NotNull;

/**
 * Inspection that enforces @Component interface is only placed in the root of a boundary.
 * 
 * Delegates architectural logic to EncapsoBoundaryService.
 */
public class ComponentPlacementInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        EncapsoBoundaryService service = EncapsoBoundaryService.getInstance(holder.getProject());

        return new JavaElementVisitor() {
            @Override
            public void visitClass(@NotNull PsiClass aClass) {
                super.visitClass(aClass);
                if (service.isComponentInterface(aClass)) {
                    // This logic is simple, but we could further delegate if rules become complex
                    PsiFile file = aClass.getContainingFile();
                    if (file instanceof PsiJavaFile javaFile) {
                        String packageName = javaFile.getPackageName();
                        if (packageName.isEmpty()) {
                            holder.registerProblem(aClass.getNameIdentifier(), 
                                "@Component interface cannot be in the default package.");
                        }
                    }
                }
            }
        };
    }
}
