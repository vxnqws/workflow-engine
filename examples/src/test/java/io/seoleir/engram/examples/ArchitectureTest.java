package io.seoleir.engram.examples;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.seoleir.engram.core.api.annotation.WorkflowInterface;

import java.time.Instant;
import java.util.UUID;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
* Arch unit tests
*/
@AnalyzeClasses(packages = "io.seoleir.engram")
public class ArchitectureTest {

    @ArchTest
    static final ArchRule workflowClassesUseOnlyPublicApi =
            noClasses().that().areAnnotatedWith(WorkflowInterface.class)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "io.seoleir.engram.core.internal..",
                            "io.seoleir.engram.runtime..",
                            "io.seoleir.engram.spi..",
                            "io.seoleir.engram.backend..",
                            "io.seoleir.engram.codec..")
                    .because("Workflow code should be bypassed by public API: annotations, Decision, WorkflowState");

    @ArchTest
    static final ArchRule publicApiDoesNotDependOnInternals =
            noClasses().that().resideInAPackage("io.seoleir.engram.core.api..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("io.seoleir.engram.core.internal..")
                    .because("Otherwise, the api/internal separation is decorative");

    @ArchTest
    static final ArchRule workflowClassesHaveNoInfrastructure =
            noClasses().that().areAnnotatedWith(WorkflowInterface.class)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "java.sql..", "javax.sql..", "java.net..", "java.nio.file..")
                    .because("Event handlers must be deterministic (I1)");

    @ArchTest
    static final ArchRule coreHasNoClockOrRandom =
            noClasses().that().resideInAPackage("io.seoleir.engram.core..")
                    .should().callMethod(Instant.class, "now")
                    .orShould().callMethod(System.class, "currentTimeMillis")
                    .orShould().callMethod(System.class, "nanoTime")
                    .orShould().callMethod(Math.class, "random")
                    .orShould().callMethod(UUID.class, "randomUUID")
                    .because("Time and randomness come to the core as event log (I1)");


    @ArchTest
    static final ArchRule coreDependsOnNothingButJdk =
            noClasses().that().resideInAPackage("io.seoleir.engram.core..")
                    .should().dependOnClassesThat()
                    .resideOutsideOfPackages("io.seoleir.engram.core..", "java..")
                    .because("Core has no external dependencies");

    @ArchTest
    static final ArchRule spiDoesNotKnowBackends =
            noClasses().that().resideInAPackage("io.seoleir.engram.spi..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("io.seoleir.engram.backend..", "io.seoleir.engram.runtime..")
                    .because("Adapters are not aware of their implementations");

    @ArchTest
    static final ArchRule backendsDoNotDependOnEachOther =
            slices().matching("io.seoleir.engram.backend.(*)..")
                    .should().notDependOnEachOther()
                    .because("Implementations of backend should not depend on each other");

    @ArchTest
    static final ArchRule backendsDoNotKnowRuntime =
            noClasses().that().resideInAPackage("io.seoleir.engram.backend..")
                    .should().dependOnClassesThat().resideInAPackage("io.seoleir.engram.runtime..")
                    .because("Backend implement only SPI");

}
