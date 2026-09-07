package io.seoleir.engram.examples;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.seoleir.engram.api.annotation.WorkflowInterface;

import java.time.Instant;
import java.util.UUID;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Architectural constraints, enforced by the build rather than by review
 */
@AnalyzeClasses(packages = "io.seoleir.engram")
public class ArchitectureTest {

    private static final String API = "io.seoleir.engram.api..";
    private static final String CORE = "io.seoleir.engram.core..";
    private static final String SPI = "io.seoleir.engram.spi..";
    private static final String RUNTIME = "io.seoleir.engram.runtime..";
    private static final String CLIENT = "io.seoleir.engram.client..";
    private static final String BACKEND = "io.seoleir.engram.backend..";
    private static final String CODEC = "io.seoleir.engram.codeccbor..";
    private static final String JDK = "java..";

    // ---- Layering ----

    @ArchTest
    static final ArchRule apiDependsOnNothingButJdk =
            noClasses().that().resideInAPackage(API)
                    .should().dependOnClassesThat()
                    .resideOutsideOfPackages(API, JDK)
                    .because("api is the public contract: it must be addable to a domain jar "
                            + "without pulling in the engine");

    @ArchTest
    static final ArchRule coreDependsOnlyOnApiAndJdk =
            noClasses().that().resideInAPackage(CORE)
                    .should().dependOnClassesThat()
                    .resideOutsideOfPackages(CORE, API, JDK)
                    .because("the core knows nothing about storage, transport or frameworks");

    @ArchTest
    static final ArchRule spiDoesNotKnowItsImplementations =
            noClasses().that().resideInAPackage(SPI)
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(BACKEND, RUNTIME, CLIENT, CODEC)
                    .because("ports must not depend on adapters");

    @ArchTest
    static final ArchRule backendsDoNotKnowRuntimeOrClient =
            noClasses().that().resideInAPackage(BACKEND)
                    .should().dependOnClassesThat().resideInAnyPackage(RUNTIME, CLIENT)
                    .because("a backend implements the SPI and nothing more");

    @ArchTest
    static final ArchRule backendsDoNotDependOnEachOther =
            slices().matching("io.seoleir.engram.backend.(*)..")
                    .should().notDependOnEachOther()
                    .as("backend implementations are interchangeable siblings")
                    .allowEmptyShould(true);

    // ---- Public API isolation ----

    @ArchTest
    static final ArchRule workflowClassesUseOnlyApi =
            noClasses().that().areAnnotatedWith(WorkflowInterface.class)
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(CORE, SPI, RUNTIME, CLIENT, BACKEND, CODEC)
                    .because("workflow code must compile against the api module alone");

    // ---- Determinism (I1) ----

    @ArchTest
    static final ArchRule workflowClassesHaveNoInfrastructure =
            noClasses().that().areAnnotatedWith(WorkflowInterface.class)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "java.sql..", "javax.sql..", "java.net..", "java.nio.file..")
                    .because("event handlers must be deterministic (I1)");

    @ArchTest
    static final ArchRule apiAndCoreHaveNoClockOrRandom =
            noClasses().that().resideInAnyPackage(API, CORE)
                    .should().callMethod(Instant.class, "now")
                    .orShould().callMethod(System.class, "currentTimeMillis")
                    .orShould().callMethod(System.class, "nanoTime")
                    .orShould().callMethod(Math.class, "random")
                    .orShould().callMethod(UUID.class, "randomUUID")
                    .because("time and randomness enter the core as event data, never as ambient state (I1)");
}