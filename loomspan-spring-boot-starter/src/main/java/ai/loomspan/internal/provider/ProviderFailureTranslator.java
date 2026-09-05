package ai.loomspan.internal.provider;

@FunctionalInterface
public interface ProviderFailureTranslator
{
    ProviderFailureDetails translate(Throwable failure);
}
