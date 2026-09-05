package ai.loomspan.sample;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class SupportedApiUsageArchitectureTest
{
    @Test
    void sampleProductionUsesOnlySupportedLoomspanApi()
    {
        noClasses()
                .that().resideInAPackage("ai.loomspan.sample..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "ai.loomspan.internal..",
                        "ai.loomspan.autoconfigure..")
                .because("sample production code must consume Loomspan only through ai.loomspan.api")
                .check(new ClassFileImporter().importPackages("ai.loomspan.sample"));
    }
}
