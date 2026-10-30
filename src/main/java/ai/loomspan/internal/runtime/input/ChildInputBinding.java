package ai.loomspan.internal.runtime.input;

import ai.loomspan.internal.core.SkillIdentity;

import java.util.Objects;

/** Immutable author-selected input transfer with a prepared producer identity. */
public final class ChildInputBinding {
    public enum SourceKind { INPUT, CHILD_RESULT }
    private final ObjectFieldPath destination;
    private final SourceKind sourceKind;
    private final ObjectFieldPath sourcePath;
    private final SkillIdentity skillIdentity;
    public ChildInputBinding(ObjectFieldPath destination, SourceKind sourceKind, ObjectFieldPath sourcePath, String skill) {
        this.destination = Objects.requireNonNull(destination);
        this.sourceKind = Objects.requireNonNull(sourceKind);
        this.sourcePath = Objects.requireNonNull(sourcePath);
        if (destination.tokens().isEmpty()) throw new IllegalArgumentException("binding destination must not be root");
        if (sourceKind == SourceKind.INPUT && skill != null) throw new IllegalArgumentException("input binding must not specify skill");
        this.skillIdentity = sourceKind == SourceKind.CHILD_RESULT ? SkillIdentity.parse(skill) : null;
    }
    @Override public boolean equals(Object other) {
        return other instanceof ChildInputBinding binding && destination.equals(binding.destination)
                && sourceKind == binding.sourceKind && sourcePath.equals(binding.sourcePath)
                && Objects.equals(skillIdentity, binding.skillIdentity);
    }
    @Override public int hashCode() { return Objects.hash(destination, sourceKind, sourcePath, skillIdentity); }
    public ObjectFieldPath destination() { return destination; }
    public SourceKind sourceKind() { return sourceKind; }
    public ObjectFieldPath sourcePath() { return sourcePath; }
    public SkillIdentity skillIdentity() { return skillIdentity; }
    public String skill() { return skillIdentity == null ? null : skillIdentity.toString(); }
}
