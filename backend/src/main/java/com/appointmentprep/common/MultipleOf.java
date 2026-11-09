package com.appointmentprep.common;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The number must be a multiple of {@link #value()}, e.g. minutes on a 5-minute grid. Null is
 * valid.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MultipleOf.Validator.class)
public @interface MultipleOf {

  int value();

  String message() default "must be a multiple of {value}";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class Validator implements ConstraintValidator<MultipleOf, Integer> {

    private int step;

    @Override
    public void initialize(MultipleOf annotation) {
      if (annotation.value() <= 0) {
        throw new IllegalArgumentException("@MultipleOf needs a positive value");
      }
      step = annotation.value();
    }

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
      return value == null || value % step == 0;
    }
  }
}
