package ai.loomspan.internal.runtime.evidence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public sealed interface EvidenceExpression permits EvidenceExpression.Skill, EvidenceExpression.AllOf, EvidenceExpression.AnyOf
{
    boolean evaluate(Set<String> successfulSkills);

    String canonical();

    Set<String> referencedSkills();

    EvidenceRequirement unsatisfiedRequirement(Set<String> successfulSkills);

    record Skill(String name, int column) implements EvidenceExpression
    {
        @Override
        public boolean evaluate(Set<String> successfulSkills)
        {
            return successfulSkills != null && successfulSkills.contains(name);
        }

        @Override
        public String canonical()
        {
            return name;
        }

        @Override
        public Set<String> referencedSkills()
        {
            return Set.of(name);
        }

        @Override
        public EvidenceRequirement unsatisfiedRequirement(Set<String> successfulSkills)
        {
            return evaluate(successfulSkills) ? null : new EvidenceRequirement("skill", name, List.of(name), List.of());
        }
    }

    record AllOf(List<EvidenceExpression> expressions) implements EvidenceExpression
    {
        public AllOf
        {
            expressions = List.copyOf(expressions);
        }

        @Override
        public boolean evaluate(Set<String> successfulSkills)
        {
            return expressions.stream().allMatch(expression -> expression.evaluate(successfulSkills));
        }

        @Override
        public String canonical()
        {
            return render(expressions, " and ", 2);
        }

        @Override
        public Set<String> referencedSkills()
        {
            return references(expressions);
        }

        @Override
        public EvidenceRequirement unsatisfiedRequirement(Set<String> successfulSkills)
        {
            if (evaluate(successfulSkills))
            {
                return null;
            }
            List<EvidenceRequirement> children = expressions.stream()
                    .map(expression -> expression.unsatisfiedRequirement(successfulSkills))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            return new EvidenceRequirement("all", canonical(), missingSkills(children), children);
        }
    }

    record AnyOf(List<EvidenceExpression> expressions) implements EvidenceExpression
    {
        public AnyOf
        {
            expressions = List.copyOf(expressions);
        }

        @Override
        public boolean evaluate(Set<String> successfulSkills)
        {
            return expressions.stream().anyMatch(expression -> expression.evaluate(successfulSkills));
        }

        @Override
        public String canonical()
        {
            return render(expressions, " or ", 1);
        }

        @Override
        public Set<String> referencedSkills()
        {
            return references(expressions);
        }

        @Override
        public EvidenceRequirement unsatisfiedRequirement(Set<String> successfulSkills)
        {
            if (evaluate(successfulSkills))
            {
                return null;
            }
            List<EvidenceRequirement> children = expressions.stream()
                    .map(expression -> expression.unsatisfiedRequirement(successfulSkills))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            return new EvidenceRequirement("any", canonical(), missingSkills(children), children);
        }
    }

    private static String render(List<EvidenceExpression> expressions, String operator, int precedence)
    {
        return expressions.stream().map(expression ->
        {
            int childPrecedence = expression instanceof AnyOf ? 1 : expression instanceof AllOf ? 2 : 3;
            String rendered = expression.canonical();
            return childPrecedence < precedence ? "(" + rendered + ")" : rendered;
        }).collect(java.util.stream.Collectors.joining(operator));
    }

    private static Set<String> references(List<EvidenceExpression> expressions)
    {
        LinkedHashSet<String> references = new LinkedHashSet<>();
        expressions.forEach(expression -> references.addAll(expression.referencedSkills()));
        return Collections.unmodifiableSet(references);
    }

    private static List<String> missingSkills(List<EvidenceRequirement> requirements)
    {
        LinkedHashSet<String> skills = new LinkedHashSet<>();
        requirements.forEach(requirement -> skills.addAll(requirement.skills()));
        return List.copyOf(skills);
    }
}
