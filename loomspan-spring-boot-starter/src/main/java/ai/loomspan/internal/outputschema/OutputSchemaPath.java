package ai.loomspan.internal.outputschema;

final class OutputSchemaPath
{
    private OutputSchemaPath()
    {
    }

    static String property(String parent, String propertyName)
    {
        if (isSimpleIdentifier(propertyName))
        {
            return parent + "." + propertyName;
        }
        return parent + "[" + jsonString(propertyName) + "]";
    }

    static String jsonString(String value)
    {
        StringBuilder escaped = new StringBuilder(value.length() + 2).append('"');
        value.codePoints().forEach(codePoint -> appendEscaped(escaped, codePoint));
        return escaped.append('"').toString();
    }

    private static boolean isSimpleIdentifier(String value)
    {
        if (value.isEmpty() || !isIdentifierStart(value.codePointAt(0)))
        {
            return false;
        }
        for (int offset = Character.charCount(value.codePointAt(0)); offset < value.length();)
        {
            int codePoint = value.codePointAt(offset);
            if (!isIdentifierPart(codePoint))
            {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    private static boolean isIdentifierStart(int codePoint)
    {
        return codePoint == '_' || codePoint >= 'A' && codePoint <= 'Z' || codePoint >= 'a' && codePoint <= 'z';
    }

    private static boolean isIdentifierPart(int codePoint)
    {
        return isIdentifierStart(codePoint) || codePoint >= '0' && codePoint <= '9';
    }

    private static void appendEscaped(StringBuilder escaped, int codePoint)
    {
        switch (codePoint)
        {
            case '"' -> escaped.append("\\\"");
            case '\\' -> escaped.append("\\\\");
            case '\b' -> escaped.append("\\b");
            case '\f' -> escaped.append("\\f");
            case '\n' -> escaped.append("\\n");
            case '\r' -> escaped.append("\\r");
            case '\t' -> escaped.append("\\t");
            default ->
            {
                if (codePoint < 0x20)
                {
                    escaped.append(String.format("\\u%04x", codePoint));
                }
                else
                {
                    escaped.appendCodePoint(codePoint);
                }
            }
        }
    }
}
