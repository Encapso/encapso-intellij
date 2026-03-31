package io.github.encapso.intellij.domain;

import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A domain representation of an Encapso Component Boundary.
 */
public record EncapsoBoundary(@NotNull String rootPackage, @NotNull PsiClass componentInterface) {
    public EncapsoBoundary {
        Objects.requireNonNull(rootPackage);
        Objects.requireNonNull(componentInterface);
    }

    @NotNull
    public String getBoundaryPackage() {
        return rootPackage;
    }

    @NotNull
    public String getComponentName() {
        return componentInterface.getName() != null ? componentInterface.getName() : "Unknown";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EncapsoBoundary that = (EncapsoBoundary) o;
        return rootPackage.equals(that.rootPackage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rootPackage);
    }
}
