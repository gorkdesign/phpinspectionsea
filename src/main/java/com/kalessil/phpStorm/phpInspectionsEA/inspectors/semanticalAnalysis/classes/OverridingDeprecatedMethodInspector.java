package com.kalessil.phpStorm.phpInspectionsEA.inspectors.semanticalAnalysis.classes;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.php.lang.psi.elements.*;
import com.kalessil.phpStorm.phpInspectionsEA.EAUltimateApplicationComponent;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpElementVisitor;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpInspection;
import com.kalessil.phpStorm.phpInspectionsEA.utils.ExpressionSemanticUtil;
import com.kalessil.phpStorm.phpInspectionsEA.utils.NamedElementUtil;
import com.kalessil.phpStorm.phpInspectionsEA.utils.OpenapiResolveUtil;
import com.kalessil.phpStorm.phpInspectionsEA.utils.OpenapiTypesUtil;
import org.jetbrains.annotations.NotNull;

/*
 * This file is part of the Php Inspections (EA Extended) package.
 *
 * (c) Vladimir Reznichenko <kalessil@gmail.com>
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */

public class OverridingDeprecatedMethodInspector extends BasePhpInspection {
    private static final String patternNeedsDeprecation      = "'%s' overrides/implements a deprecated method. Consider refactoring or deprecate it as well.";
    private static final String patternDeprecateParent       = "Parent '%s' probably needs to be deprecated as well.";
    private static final String patternMissingDeprecationTag = "'%s' triggers a deprecation warning, but misses @deprecated annotation.";

    @NotNull
    public String getShortName() {
        return "OverridingDeprecatedMethodInspection";
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new BasePhpElementVisitor() {
            @Override
            public void visitPhpFunctionCall(@NotNull FunctionReference reference) {
                if (!EAUltimateApplicationComponent.areFeaturesEnabled()) { return; }
                if (this.isContainingFileSkipped(reference))              { return; }

                final String functionName = reference.getName();
                if (functionName != null && functionName.equals("trigger_error")) {
                    final PsiElement[] arguments = reference.getParameters();
                    if (arguments.length == 2 && arguments[1].getText().equals("E_USER_DEPRECATED")) {
                        final Function scope = ExpressionSemanticUtil.getScope(reference);
                        if (scope != null) {
                            final GroupStatement body = ExpressionSemanticUtil.getGroupStatement(scope);
                            if (body != null) {
                                PsiElement parent = reference.getParent();
                                parent            = parent instanceof UnaryExpression ? parent.getParent() : parent;
                                if (OpenapiTypesUtil.isStatementImpl(parent) && parent.getParent() == body && !scope.isDeprecated()) {
                                    final PsiElement nameNode = NamedElementUtil.getNameIdentifier(scope);
                                    if (nameNode != null ) {
                                        holder.registerProblem(nameNode, String.format(patternMissingDeprecationTag, scope.getName()));
                                    }
                                }
                            }
                        }
                    }
                }
            }

            @Override
            public void visitPhpMethod(@NotNull Method method) {
                if (!EAUltimateApplicationComponent.areFeaturesEnabled()) { return; }
                if (this.isContainingFileSkipped(method))                 { return; }

                /* do not process un-reportable classes and interfaces - we are searching real tech. debt here */
                final PhpClass clazz      = method.getContainingClass();
                final PsiElement nameNode = NamedElementUtil.getNameIdentifier(method);
                if (clazz != null && nameNode != null) {
                    final String methodName    = method.getName();
                    final boolean isDeprecated = method.isDeprecated();

                    /* case: deprecated parent methods */
                    final PhpClass parent = OpenapiResolveUtil.resolveSuperClass(clazz);
                    if (parent != null) {
                        final Method parentMethod = OpenapiResolveUtil.resolveMethod(parent, methodName);
                        if (parentMethod != null) {
                            if (!isDeprecated && parentMethod.isDeprecated()) {
                                holder.registerProblem(nameNode, String.format(patternNeedsDeprecation, methodName));
                                return;
                            }
                            if (isDeprecated && !parentMethod.isDeprecated()) {
                                holder.registerProblem(nameNode, String.format(patternDeprecateParent, methodName));
                                return;
                            }
                        }
                    }

                    if (!isDeprecated) {
                        /* case: deprecated interface methods */
                        for (final PhpClass contract : OpenapiResolveUtil.resolveImplementedInterfaces(clazz)) {
                            final Method contractMethod = OpenapiResolveUtil.resolveMethod(contract, methodName);
                            if (contractMethod != null && contractMethod.isDeprecated()) {
                                holder.registerProblem(nameNode, String.format(patternNeedsDeprecation, methodName));
                                return;
                            }
                        }
                        /* case: deprecated trait methods */
                        for (final PhpClass trait : OpenapiResolveUtil.resolveImplementedTraits(clazz)) {
                            final Method traitMethod = OpenapiResolveUtil.resolveMethod(trait, methodName);
                            if (traitMethod != null && traitMethod.isDeprecated()) {
                                holder.registerProblem(nameNode, String.format(patternNeedsDeprecation, methodName));
                                return;
                            }
                        }
                    }
                }
            }
        };
    }
}
