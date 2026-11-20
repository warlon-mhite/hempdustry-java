/**
 * The rules of the genetics: how a plant rolls its genes, how a harvest rolls its tier, what a band, a tier, a
 * chemotype or a resin step does in the oven, the bowl and the sieve, and how a bowl is dosed. <b>Not API.</b>
 *
 * <p>Other mods read the facts, never the rules: a strain's sheet ({@code Strain.traits()}), and what a stack or a
 * load carries (its {@code Chemotype}, {@code PotencyBand}, {@code BudTier}, {@code ResinStep}), from the types in
 * {@code com.warlonmhite.hempdustry.strain}, which keep their shape from release to release. Everything here may
 * change in any release: the breeding update replaces the rolls with inheritance, and balance moves the numbers.
 * Nothing in the frozen surface names a type of this package, which is what keeps it out of that promise.
 */
package com.warlonmhite.hempdustry.strain.internal;
