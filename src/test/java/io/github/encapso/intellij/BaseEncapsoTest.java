package io.github.encapso.intellij;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base class for Encapso plugin tests using JUnit 5 and Java.
 * Extends the modern JUnit 5-native fixture.
 */
public abstract class BaseEncapsoTest extends LightJavaCodeInsightFixtureTestCase5 {

    @Override
    public String getTestDataPath() {
        return "src/test/testData";
    }

    @BeforeEach
    public void setUpEncapso() {
        // Setup mock Encapso environment with explicit file paths for reliable resolution
        getFixture().addFileToProject("io/github/encapso/Component.java", """
            package io.github.encapso;
            import java.lang.annotation.*;
            @Target(ElementType.TYPE)
            @Retention(RetentionPolicy.RUNTIME)
            public @interface Component {}
        """);

        getFixture().addFileToProject("io/github/encapso/Api.java", """
            package io.github.encapso;
            import java.lang.annotation.*;
            @Target(ElementType.TYPE)
            @Retention(RetentionPolicy.RUNTIME)
            public @interface Api {}
        """);

        getFixture().addFileToProject("io/github/encapso/DelegateTo.java", """
            package io.github.encapso;
            import java.lang.annotation.*;
            @Target(ElementType.METHOD)
            @Retention(RetentionPolicy.RUNTIME)
            public @interface DelegateTo {
                Class<?> value();
            }
        """);
    }
}
