package io.github.encapso.intellij.inspection;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.encapso.intellij.domain.EncapsoBoundary;
import io.github.encapso.intellij.service.EncapsoBoundaryService;
import org.jetbrains.annotations.NotNull;

/**
 * Inspection to enforce that internal implementation classes do not refer to 
 * their own @Component interface or its generated Builder.
 * 
 * Delegates Encapso-specific logic to EncapsoBoundaryService to follow SOLID principles.
 */
public class InternalComponentReferenceInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        EncapsoBoundaryService service = EncapsoBoundaryService.getInstance(holder.getProject());

        return new JavaElementVisitor() {
            @Override
            public void visitTypeElement(@NotNull PsiTypeElement typeElement) {
                super.visitTypeElement(typeElement);
                checkReference(typeElement, holder, service);
            }

            @Override
            public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
                super.visitReferenceExpression(expression);
                checkReference(expression, holder, service);
            }

            @Override
            public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
                super.visitReferenceElement(reference);
                checkReference(reference, holder, service);
            }
        };
    }

    private void checkReference(PsiElement element, ProblemsHolder holder, EncapsoBoundaryService service) {
        PsiClass referencedClass = resolveClass(element);
        if (referencedClass == null) return;

        PsiClass callerClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        if (callerClass == null || callerClass.equals(referencedClass)) return;

        // Exempt Builders and Impl classes from self-reference checks (they define the boundary)
        String callerName = callerClass.getName();
        if (callerName != null && (callerName.endsWith("Builder") || callerName.endsWith("Impl"))) {
            return;
        }

        EncapsoBoundary referencedBoundary = service.findBoundary(referencedClass);
        EncapsoBoundary callerBoundary = service.findBoundary(callerClass);

        // Rule: If they are in the same boundary, we restrict referencing the Component and the Builder
        if (referencedBoundary != null && referencedBoundary.equals(callerBoundary)) {
            boolean isComponent = service.isComponentInterface(referencedClass);
            boolean isBuilder = service.isBuilder(referencedClass, referencedBoundary);

            if (isComponent || isBuilder) {
                holder.registerProblem(
                        element,
                        String.format("Internal class '%s' cannot refer to its own component interface '%s'. " +
                                      "This maintains a strict architectural boundary between implementation and API.",
                                callerClass.getName(), referencedClass.getName())
                );
            }
        }
    }

    private PsiClass resolveClass(PsiElement element) {
        if (element instanceof PsiTypeElement typeElement && typeElement.getType() instanceof PsiClassType classType) {
            return classType.resolve();
        } else if (element instanceof PsiReferenceExpression refExpr && refExpr.resolve() instanceof PsiClass aClass) {
            return aClass;
        } else if (element instanceof PsiJavaCodeReferenceElement refElement && refElement.resolve() instanceof PsiClass aClass) {
            return aClass;
        }
        return null;
    }
}
