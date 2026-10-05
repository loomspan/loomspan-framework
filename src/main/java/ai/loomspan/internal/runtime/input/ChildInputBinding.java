package ai.loomspan.internal.runtime.input;

import java.util.Objects;

/** Immutable author-selected input transfer. */
public record ChildInputBinding(ObjectFieldPath destination, SourceKind sourceKind, ObjectFieldPath sourcePath, String skill) {
    public enum SourceKind { INPUT, CHILD_RESULT }
    public ChildInputBinding {
        Objects.requireNonNull(destination); Objects.requireNonNull(sourceKind); Objects.requireNonNull(sourcePath);
        if (destination.tokens().isEmpty()) throw new IllegalArgumentException("binding destination must not be root");
        if (sourceKind == SourceKind.CHILD_RESULT && (skill == null || skill.isBlank() || !skill.equals(skill.strip())))
            throw new IllegalArgumentException("child_result binding requires an exact nonblank skill name");
        if (sourceKind == SourceKind.INPUT && skill != null) throw new IllegalArgumentException("input binding must not specify skill");
    }
}
