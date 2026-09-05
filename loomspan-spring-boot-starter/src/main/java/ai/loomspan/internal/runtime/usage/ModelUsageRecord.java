package ai.loomspan.internal.runtime.usage;

import org.springframework.lang.Nullable;

public record ModelUsageRecord(int promptUnits, int completionUnits, int totalUnits, UsagePrecision precision, @Nullable Object nativeUsage)
{
    public ModelUsageRecord
    {
        if (promptUnits < 0)
        {
            throw new IllegalArgumentException("promptUnits must not be negative");
        }
        if (completionUnits < 0)
        {
            throw new IllegalArgumentException("completionUnits must not be negative");
        }
        if (totalUnits < 0)
        {
            throw new IllegalArgumentException("totalUnits must not be negative");
        }
        if (totalUnits < promptUnits || totalUnits < completionUnits)
        {
            throw new IllegalArgumentException("totalUnits must be greater than or equal to component units");
        }
        if (precision == null)
        {
            throw new IllegalArgumentException("precision must not be null");
        }
    }
}
