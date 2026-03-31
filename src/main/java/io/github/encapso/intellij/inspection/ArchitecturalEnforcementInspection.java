package io.github.encapso.intellij.inspection;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.encapso.engine.Violation;
import io.github.encapso.intellij.service.EncapsoBoundaryService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Main architectural enforcement inspection for Encapso.
 * 
 * Reports all architectural violations as hard compilation errors (red waves).
 */
public class ArchitecturalEnforcementInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        EncapsoBoundaryService service = EncapsoBoundaryService.getInstance(holder.getProject());

        return new JavaElementVisitor() {
            @Override
            public void visitImportStatement(@NotNull PsiImportStatement statement) {
                super.visitImportStatement(statement);
                
                if (statement.isOnDemand()) {
                    PsiJavaCodeReferenceElement ref = statement.getImportReference();
                    if (ref != null) {
                        String pkgName = ref.getQualifiedName();
                        if (service.findBoundaryByPackage(pkgName) != null) {
                            holder.registerProblem(
                                    statement,
                                    "Architecture violation: Wildcard imports from component packages are not allowed. Use explicit imports instead.",
                                    ProblemHighlightType.GENERIC_ERROR
                            );
                        }
                    }
                    return;
                }

                PsiElement resolved = statement.resolve();
                if (resolved instanceof PsiClass aClass) {
                    checkAccess(statement, holder, service, aClass);
                }
            }

            @Override
            public void visitTypeElement(@NotNull PsiTypeElement typeElement) {
                super.visitTypeElement(typeElement);
                checkAccess(typeElement, holder, service, null);
            }

            @Override
            public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
                super.visitReferenceExpression(expression);
                checkAccess(expression, holder, service, null);
            }

            @Override
            public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
                super.visitReferenceElement(reference);
                // Catch all direct class references (e.g. new org.example.componentA.B())
                checkAccess(reference, holder, service, null);
            }
        };
    }

    private void checkAccess(PsiElement element, ProblemsHolder holder, EncapsoBoundaryService service, @Nullable PsiClass resolvedClass) {
        PsiClass referencedClass = (resolvedClass != null) ? resolvedClass : resolveClass(element);
        if (referencedClass == null) return;

        // Skip self-references (within the same class)
        PsiClass callerClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        if (callerClass != null && callerClass.equals(referencedClass)) return;

        Optional<Violation> violation = service.checkViolation(referencedClass, element);
        if (violation.isPresent()) {
            String reason = determineReason(violation.get(), referencedClass);
            holder.registerProblem(
                    element,
                    reason,
                    ProblemHighlightType.GENERIC_ERROR
            );
        }
    }

    private String determineReason(Violation violation, PsiClass referencedClass) {
        if (violation.isSelfReference()) {
            // Rule 2 & 3: Illegal internal dependency leakage
            if (referencedClass.isInterface()) {
                return String.format("Architecture violation [Rule 2]: Internal implementation cannot refer to its own component interface '%s'. Use a collaborator or dependency instead.",
                        referencedClass.getName());
            } else {
                return String.format("Architecture violation [Rule 3]: Component API cannot refer to its own internal implementation '%s'.",
                        referencedClass.getName());
            }
        } else {
            // Rule 1: Public leak
            return String.format("Architecture violation [Rule 1]: Access to internal implementation '%s' is restricted to component '%s'.",
                    referencedClass.getName(), violation.componentPackage());
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
