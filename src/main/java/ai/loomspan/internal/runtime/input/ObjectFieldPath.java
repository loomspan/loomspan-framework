package ai.loomspan.internal.runtime.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Object-only JSON Pointer: numeric tokens are ordinary named object keys. */
public record ObjectFieldPath(String pointer, List<String> tokens) {
    public ObjectFieldPath { Objects.requireNonNull(pointer); tokens = List.copyOf(tokens); }
    public static ObjectFieldPath parse(String pointer, boolean allowRoot) {
        Objects.requireNonNull(pointer, "path must not be null");
        if (pointer.isEmpty()) {
            if (!allowRoot) throw new IllegalArgumentException("destination must select a named object field");
            return new ObjectFieldPath("", List.of());
        }
        if (!pointer.startsWith("/")) throw new IllegalArgumentException("path must be an object JSON Pointer");
        List<String> tokens = new ArrayList<>();
        for (String raw : pointer.substring(1).split("/", -1)) {
            StringBuilder token = new StringBuilder();
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                if (c != '~') token.append(c);
                else {
                    if (++i >= raw.length() || (raw.charAt(i) != '0' && raw.charAt(i) != '1'))
                        throw new IllegalArgumentException("invalid JSON Pointer escape in " + pointer);
                    token.append(raw.charAt(i) == '0' ? '~' : '/');
                }
            }
            tokens.add(token.toString());
        }
        return new ObjectFieldPath(pointer, tokens);
    }
    public boolean isAncestorOf(ObjectFieldPath other) {
        return tokens.size() <= other.tokens.size() && tokens.equals(other.tokens.subList(0, tokens.size()));
    }
}
