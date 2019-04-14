package com.kalessil.phpStorm.phpInspectionsEA.inspectors.semanticalAnalysis.classes;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.kalessil.phpStorm.phpInspectionsEA.EAUltimateApplicationComponent;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpElementVisitor;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpInspection;
import com.kalessil.phpStorm.phpInspectionsEA.utils.NamedElementUtil;
import com.kalessil.phpStorm.phpInspectionsEA.utils.OpenapiEquivalenceUtil;
import com.kalessil.phpStorm.phpInspectionsEA.utils.OpenapiResolveUtil;
import com.kalessil.phpStorm.phpInspectionsEA.utils.hierarhy.InterfacesExtractUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/*
 * This file is part of the Php Inspections (EA Extended) package.
 *
 * (c) Vladimir Reznichenko <kalessil@gmail.com>
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */

public class ClassReusesParentTraitInspector extends BasePhpInspection {
    private static final String patternDirectDuplication   = "'%s' is already used.";
    private static final String patternIndirectDuplication = "'%s' is already used in '%s'.";

    @NotNull
    public String getShortName() {
        return "ClassReusesParentTraitInspection";
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new BasePhpElementVisitor() {
            @Override
            public void visitPhpClass(@NotNull PhpClass clazz) {
                if (!EAUltimateApplicationComponent.areFeaturesEnabled()) { return; }
                if (this.isContainingFileSkipped(clazz))                  { return; }

                if (!clazz.isInterface() && clazz.hasTraitUses()) {
                    final Set<PhpClass> classes = InterfacesExtractUtil.getCrawlInheritanceTree(clazz, true);
                    if (!classes.isEmpty()) {
                        final Map<PhpClass, List<String>> registry = new HashMap<>();
                        classes.forEach(c -> this.collectTraits(c, registry));

                        final Set<String> processed = new HashSet<>();
                        final List<String> traits   = registry.get(clazz);
                        for (final Map.Entry<PhpClass, List<String>> entry : registry.entrySet()) {
                            final PhpClass subject = entry.getKey();
                            if (subject == clazz)  {
                                /* case 1: direct traits duplication */
                                entry.getValue().stream().filter(candidate  -> !processed.contains(candidate))
                                        .forEach(candidate -> {
                                            final boolean hasDuplicates = traits.stream().filter(trait -> trait.equals(candidate)).count() > 1L;
                                            if (hasDuplicates && processed.add(candidate)) {
                                                holder.registerProblem(
                                                        NamedElementUtil.getNameIdentifier(clazz),
                                                        String.format(patternDirectDuplication, candidate)
                                                );
                                            }
                                        });
                            } else {
                                /* case 2: indirect traits duplication */
                                entry.getValue().stream().filter(candidate  -> !processed.contains(candidate))
                                        .forEach(candidate -> {
                                            final boolean hasDuplicates = traits.stream().anyMatch(trait -> trait.equals(candidate));
                                            if (hasDuplicates && processed.add(candidate)) {
                                                holder.registerProblem(
                                                        NamedElementUtil.getNameIdentifier(clazz),
                                                        String.format(patternIndirectDuplication, candidate, subject.getFQN())
                                                );
                                            }
                                        });
                            }
                        }
                        processed.clear();

                        registry.values().forEach(List::clear);
                        registry.clear();
                        classes.clear();
                    }
                }
            }

            private void collectTraits(@NotNull PhpClass clazz, @NotNull Map<PhpClass, List<String>> storage) {
                if (!clazz.isInterface() && !storage.containsKey(clazz) && clazz.hasTraitUses()) {
                    storage.computeIfAbsent(clazz, (key) -> new ArrayList<>());
                    for (final PhpClass trait : OpenapiResolveUtil.resolveImplementedTraits(clazz)) {
                        storage.get(clazz).add(trait.getFQN());
                        this.collectTraits(trait, storage);
                    }
                }
            }
        };
    }
}
