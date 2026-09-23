package com.smarthire.application;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Architecture fitness rules for application-service (governance §46). */
@AnalyzeClasses(
    packages = "com.smarthire.application",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  private static final DescribedPredicate<JavaClass> IN_DOMAIN = resideInAPackage("..domain..");

  @ArchTest
  static final ArchRule domainDependsOnNothingAboveIt =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..service..", "..api..", "..infra..", "..security..");

  @ArchTest
  static final ArchRule serviceDoesNotDependOnApi =
      noClasses()
          .that()
          .resideInAPackage("..service..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..api..");

  @ArchTest
  static final ArchRule infraDoesNotDependOnApiOrService =
      noClasses()
          .that()
          .resideInAPackage("..infra..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..api..", "..service..");

  @ArchTest
  static final ArchRule controllersResideInApiPackage =
      classes().that().haveSimpleNameEndingWith("Controller").should().resideInAPackage("..api..");

  @ArchTest
  static final ArchRule entitiesResideInDomainPackage =
      classes()
          .that()
          .areAnnotatedWith("jakarta.persistence.Entity")
          .should()
          .resideInAPackage("..domain..");

  @ArchTest
  static final ArchRule repositoriesResideInInfraPackage =
      classes()
          .that()
          .areAssignableTo(org.springframework.data.repository.Repository.class)
          .should()
          .resideInAPackage("..infra..");

  @ArchTest
  static final ArchRule feignClientsLiveOnlyInInfra =
      noClasses()
          .that()
          .resideOutsideOfPackage("..infra..")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("org.springframework.cloud.openfeign.FeignClient")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule noCrossServiceImports =
      noClasses()
          .that()
          .resideInAPackage("com.smarthire.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.smarthire.auth..",
              "com.smarthire.job..",
              "com.smarthire.recruitment..",
              "com.smarthire.document..",
              "com.smarthire.notification..")
          .allowEmptyShould(true);
}
