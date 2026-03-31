package io.github.encapso.intellij.inspection;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.encapso.intellij.domain.EncapsoBoundary;
import io.github.encapso.intellij.service.EncapsoBoundaryService;
import org.jetbrains.annotations.NotNull;

/**
 * Inspection that prevents @Component interfaces from delegating to their internal 
 * implementation classes.
 */
public class StaticDelegationInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        EncapsoBoundaryService service = EncapsoBoundaryService.getInstance(holder.getProject());

        return new JavaElementVisitor() {
            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
                super.visitMethodCallExpression(expression);

                PsiClass callerClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
                if (callerClass == null || !service.isComponentInterface(callerClass)) return;

                EncapsoBoundary boundary = service.findBoundary(callerClass);
                if (boundary == null) return;

                PsiMethod method = expression.resolveMethod();
                if (method == null) return;

                PsiClass referencedClass = method.getContainingClass();
                if (referencedClass == null) return;

                // Rule: If it's the implementation class for the same boundary, we block the delegation.
                if (service.isImplementation(referencedClass, boundary)) {
                    holder.registerProblem(
                            expression,
                            String.format("Delegation to internal implementation class '%s' is not allowed from the component interface. " +
                                          "Use a non-static instance method within an Implementation instead.",
                                          referencedClass.getName())
                    );
                }
            }
        };
    }
}
