package com.smarthire.auth;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * ArchUnit fitness rules for auth-service (governance §46), mirroring job-service's. Auth-service
 * legitimately depends on {@code com.smarthire.common}, so common is not in the cross-service ban
 * list — only the other business services are.
 */
@AnalyzeClasses(
    packages = "com.smarthire.auth",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  private static final DescribedPredicate<JavaClass> IN_DOMAIN = resideInAPackage("..domain..");

  @ArchTest static final ArchRule no_standard_streams = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

  @ArchTest
  static final ArchRule domain_depends_on_nothing_above_it =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..service..", "..api..");

  @ArchTest
  static final ArchRule service_does_not_depend_on_api =
      noClasses()
          .that()
          .resideInAPackage("..service..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..api..");

  // §46 "controllers return DTOs not entities". Checks the full generic return type, not just its
  // erasure, so a leak wrapped in a generic (List<User>, Optional<User>, ResponseEntity<User>, …)
  // is caught, where a raw-return-type rule would miss it.
  @ArchTest
  static final ArchRule controllers_return_dtos_not_entities =
      methods()
          .that()
          .areDeclaredInClassesThat()
          .resideInAPackage("..api..")
          .should(notReturnDomainTypesAnywhereInTheGenericSignature())
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule feign_clients_live_only_in_infra =
      noClasses()
          .that()
          .resideOutsideOfPackage("..infra..")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("org.springframework.cloud.openfeign.FeignClient")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule no_cross_service_imports =
      noClasses()
          .that()
          .resideInAPackage("com.smarthire.auth..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.smarthire.job..",
              "com.smarthire.application..",
              "com.smarthire.recruitment..",
              "com.smarthire.document..",
              "com.smarthire.notification..")
          .allowEmptyShould(true);

  private static ArchCondition<JavaMethod> notReturnDomainTypesAnywhereInTheGenericSignature() {
    return new ArchCondition<>("not return domain types (including inside generics)") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        Set<JavaClass> erasures = new LinkedHashSet<>();
        collectErasures(method.getReturnType(), erasures);
        for (JavaClass returned : erasures) {
          if (IN_DOMAIN.test(returned)) {
            events.add(
                SimpleConditionEvent.violated(
                    method, method.getFullName() + " returns domain type " + returned.getName()));
            return;
          }
        }
      }
    };
  }

  /** Erasure of the type plus, recursively, the erasures of any generic type arguments. */
  private static void collectErasures(JavaType type, Set<JavaClass> out) {
    out.add(type.toErasure());
    if (type instanceof JavaParameterizedType parameterized) {
      for (JavaType argument : parameterized.getActualTypeArguments()) {
        collectErasures(argument, out);
      }
    }
  }
}
