package edu.sjsu.spring2026.group32.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Marks code that should be excluded from JaCoCo coverage.
 * Used for UI wiring, framework glue, or code that isn't reasonable to cover w/ unit tests.
 */
@Retention(RetentionPolicy.CLASS)
public @interface GeneratedExcludeFromCoverage {}