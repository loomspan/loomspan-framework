package ai.loomspan.internal.model;

@FunctionalInterface
public interface ModelInteraction
{
    ModelInteractionResult call(ModelInteractionRequest request);
}
