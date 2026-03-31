package io.github.encapso.intellij.service;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.encapso.engine.BoundaryRegistry;
import io.github.encapso.engine.EncapsoEngine;
import io.github.encapso.engine.EncapsoType;
import io.github.encapso.engine.Violation;
import io.github.encapso.intellij.core.EncapsoConstants;
import io.github.encapso.intellij.domain.EncapsoBoundary;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Service providing core Encapso architectural boundary operations.
 * High Performance Architectural Boundary Service.
 * Centralizes all boundary discovery and accessibility logic.
 */
public class EncapsoBoundaryService {

    private final Project project;
    private final BoundaryRegistry registry;
    private final EncapsoEngine engine;

    public EncapsoBoundaryService(Project project) {
        this.project = project;
        this.registry = new BoundaryRegistry();
        this.engine = new EncapsoEngine(registry);
    }

    /**
     * Returns the singleton instance of the service for the given project.
     */
    @NotNull
    public static EncapsoBoundaryService getInstance(@NotNull Project project) {
        return project.getService(EncapsoBoundaryService.class);
    }

    /**
     * Determines if a class is the component interface itself.
     */
    public boolean isComponentInterface(@NotNull PsiClass psiClass) {
        return AnnotationUtil.findAnnotation(psiClass, EncapsoConstants.COMPONENT_FQN) != null;
    }

    /**
     * Determines if a class is a public API annotated with @Api.
     */
    public boolean isPublicApi(@NotNull PsiClass psiClass) {
        return AnnotationUtil.findAnnotation(psiClass, EncapsoConstants.API_FQN) != null;
    }

    /**
     * Determines if a class is an authorized entry point for the specified boundary.
     */
    public boolean isEntryPoint(@NotNull PsiClass psiClass, @NotNull EncapsoBoundary boundary) {
        return engine.isEntryPoint(toType(psiClass), boundary.getBoundaryPackage());
    }

    /**
     * Determines if a class is the generated builder for a boundary.
     */
    public boolean isBuilder(@NotNull PsiClass psiClass, @NotNull EncapsoBoundary boundary) {
        return engine.isBuilder(toType(psiClass), boundary.getBoundaryPackage());
    }

    /**
     * Determines if a class is an internal implementation for a boundary.
     */
    public boolean isImplementation(@NotNull PsiClass psiClass, @NotNull EncapsoBoundary boundary) {
        String fqn = psiClass.getQualifiedName();
        return fqn != null && fqn.startsWith(boundary.getBoundaryPackage()) && (fqn.contains(".impl.") || fqn.endsWith("Impl"));
    }

    /**
     * Determines if a candidate class is architecturally accessible from a caller element.
     */
    public boolean isAccessible(@NotNull PsiClass psiClass, @NotNull PsiElement caller) {
        return checkViolation(psiClass, caller).isEmpty();
    }

    /**
     * Validates a type reference and reports any violations.
     */
    @NotNull
    public Optional<Violation> checkViolation(@NotNull PsiClass refClass, @NotNull PsiElement caller) {
        String refPkg = getPackageName(refClass);
        String callerPkg = getPackageName(caller);

        if (refPkg == null || callerPkg == null) return Optional.empty();

        // 1. Authoritative Hydration: Ensure boundary is resolved before engine call
        findBoundaryPackageForensically(refPkg);
        findBoundaryPackageForensically(callerPkg);

        PsiClass callerClass = getParentClass(caller);
        String refFqn = (refClass.getQualifiedName() != null) ? refClass.getQualifiedName() : refPkg + "." + refClass.getName();
        String callerFqn = (callerClass != null) ? (callerClass.getQualifiedName() != null ? callerClass.getQualifiedName() : callerPkg + "." + callerClass.getName()) : callerPkg + ".Context";

        // 2a. Dynamic Forensic @Api Registration
        // If the referenced class has @Api, we must register it as public in the engine 
        // to prevent Rule 1 false-positives during completion/inspection.
        if (isPublicApi(refClass)) {
            engine.registerPublicType(refPkg, refFqn);
        }

        // 2b. High Forensic Rule 3 (Final Barrier for completion/inspection)
        if (refFqn.endsWith("Impl") || refFqn.contains(".impl.")) {
             if (callerFqn.equals(refFqn)) return Optional.empty();
             
             // If caller is NOT a builder and NOT the implementation, it's Rule 3
             // unless it's a legitimate self-reference during forensic discovery.
             if (!callerFqn.endsWith("Builder") && !callerFqn.endsWith("Impl")) {
                  return Optional.of(new Violation(refPkg, true, callerFqn, refFqn));
             }
        }

        return engine.checkViolation(new EncapsoType(refFqn, refPkg), callerPkg, callerFqn);
    }

    /**
     * Validates a file reference and reports any violations.
     */
    @NotNull
    public Optional<Violation> checkViolation(@NotNull PsiFile refFile, @NotNull PsiElement caller) {
        if (refFile instanceof PsiJavaFile javaFile) {
            PsiClass[] classes = javaFile.getClasses();
            if (classes.length > 0) {
                return checkViolation(classes[0], caller);
            }
        }
        return Optional.empty();
    }

    /**
     * Resolves the Encapso boundary for a given PSI element.
     */
    @Nullable
    public EncapsoBoundary findBoundary(@Nullable PsiElement element) {
        if (element == null) return null;
        String pkg = getPackageName(element);
        return findBoundaryByPackage(pkg);
    }

    /**
     * Resolves the Encapso boundary for a given packageName.
     */
    @Nullable
    public EncapsoBoundary findBoundaryByPackage(@Nullable String packageName) {
        if (packageName == null) return null;
        String boundaryPackage = findBoundaryPackageForensically(packageName);
        if (boundaryPackage == null) return null;

        String interfaceFqn = registry.getInterface(boundaryPackage);
        if (interfaceFqn == null) return null;

        PsiClass componentInterface = JavaPsiFacade.getInstance(project).findClass(interfaceFqn, GlobalSearchScope.allScope(project));
        if (componentInterface == null) return null;

        return new EncapsoBoundary(boundaryPackage, componentInterface);
    }

    @Nullable
    private String findBoundaryPackageForensically(String pkg) {
        String existing = registry.findBoundary(pkg);
        if (existing != null) return existing;

        String currentPkg = pkg;
        while (currentPkg != null && !currentPkg.isEmpty()) {
            PsiClass componentInterface = discoverComponentInterfaceInPackage(currentPkg);
            if (componentInterface != null) {
                String interfaceFqn = componentInterface.getQualifiedName();
                if (interfaceFqn != null) {
                    registry.registerBoundary(currentPkg, interfaceFqn);
                    return currentPkg;
                }
            }
            int lastDot = currentPkg.lastIndexOf('.');
            currentPkg = (lastDot > 0) ? currentPkg.substring(0, lastDot) : null;
        }
        return null;
    }

    @Nullable
    private PsiClass discoverComponentInterfaceInPackage(String pkg) {
        // High Fidelity: Forensic Discovery via Short Names
        int lastDot = pkg.lastIndexOf('.');
        String pkgSimpleName = (lastDot > 0) ? pkg.substring(lastDot + 1) : pkg;
        String baseName = capitalize(pkgSimpleName);
        
        String[] possibleNames = {baseName, baseName + "Component"};
        PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        
        for (String name : possibleNames) {
            for (PsiClass cls : cache.getClassesByName(name, scope)) {
                if (pkg.equals(getPackageName(cls)) && isComponentInterface(cls)) return cls;
            }
        }

        // Fallback: search all classes in the package
        PsiPackage psiPackage = JavaPsiFacade.getInstance(project).findPackage(pkg);
        if (psiPackage != null) {
            for (PsiClass cls : psiPackage.getClasses()) {
                if (isComponentInterface(cls)) return cls;
            }
        }
        
        return null;
    }

    private String getPackageName(PsiElement element) {
        if (element instanceof PsiClass pc) {
            String fqn = pc.getQualifiedName();
            if (fqn != null && fqn.contains(".")) {
                return fqn.substring(0, fqn.lastIndexOf('.'));
            }
            // Mock Fallback
            PsiFile file = pc.getContainingFile();
            if (file instanceof PsiJavaFile javaFile) {
                return javaFile.getPackageName();
            }
        }
        PsiFile file = element.getContainingFile();
        if (file instanceof PsiJavaFile javaFile) {
            return javaFile.getPackageName();
        }
        return null;
    }

    @Nullable
    private PsiClass getParentClass(PsiElement element) {
        return PsiTreeUtil.getParentOfType(element, PsiClass.class);
    }

    @NotNull
    private String getAbsoluteFqn(@NotNull PsiClass psiClass) {
        String fqn = psiClass.getQualifiedName();
        return (fqn != null) ? fqn : (psiClass.getName() != null ? psiClass.getName() : "");
    }

    @NotNull
    private EncapsoType toType(@NotNull PsiClass psiClass) {
        return new EncapsoType(getAbsoluteFqn(psiClass), getPackageName(psiClass));
    }

    @NotNull
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
