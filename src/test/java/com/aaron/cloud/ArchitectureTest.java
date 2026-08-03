package com.aaron.cloud;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(location -> location.contains("/com/aaron/cloud/"))
            .importClasspath();

    @Test
    void classpathContainsProjectClasses() {
        assertTrue(
                CLASSES.stream().anyMatch(javaClass -> javaClass.getPackageName().startsWith("com.aaron.cloud.common")),
                "ArchUnit 未扫描到 com.aaron.cloud 包域类，请确认单模块工程已编译且在测试 classpath 上");
    }

    @Test
    void commonMustNotDependOnDomainModules() {
        ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage("com.aaron.cloud.common..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.aaron.cloud.chat..",
                        "com.aaron.cloud.rag..",
                        "com.aaron.cloud.model..",
                        "com.aaron.cloud.gateway..",
                        "com.aaron.cloud.identity..",
                        "com.aaron.cloud.job..",
                        "com.aaron.cloud.file..",
                        "com.aaron.cloud.mcp..",
                        "com.aaron.cloud.notification..",
                        "com.aaron.cloud.eval..",
                        "com.aaron.cloud.scheduled..")
                .check(CLASSES);
    }

    @Test
    void chatMustNotDependOnRagImplementation() {
        ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage("com.aaron.cloud.chat..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.aaron.cloud.rag..")
                .check(CLASSES);
    }

    @Test
    void ragMustNotDependOnChat() {
        ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage("com.aaron.cloud.rag..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.aaron.cloud.chat..")
                .check(CLASSES);
    }
}
