package ai.loomspan.internal.core;

import java.util.Map;

@FunctionalInterface
public interface CapabilityInvoker
{
    Object invoke(Map<String, Object> arguments);
}
