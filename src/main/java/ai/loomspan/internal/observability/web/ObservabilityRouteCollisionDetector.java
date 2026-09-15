package ai.loomspan.internal.observability.web;

import org.springframework.http.server.PathContainer;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.support.RouterFunctionMapping;
import org.springframework.web.servlet.handler.AbstractUrlHandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ObservabilityRouteCollisionDetector
{
    private final PathPatternParser parser = new PathPatternParser();
    private final List<HandlerMapping> handlerMappings;

    public ObservabilityRouteCollisionDetector(List<HandlerMapping> handlerMappings)
    {
        this.handlerMappings = List.copyOf(handlerMappings);
    }

    public boolean hasCollision()
    {
        for (HandlerMapping mapping : handlerMappings)
        {
            if (mapping instanceof RequestMappingInfoHandlerMapping requestMappings
                    && handlerMethodCollision(requestMappings))
            {
                return true;
            }
            if (mapping instanceof AbstractUrlHandlerMapping urlMappings
                    && explicitUrlCollision(urlMappings))
            {
                return true;
            }
            if (mapping instanceof RouterFunctionMapping routerMappings
                    && functionalCollision(routerMappings.getRouterFunction()))
            {
                return true;
            }
        }
        return false;
    }

    private boolean handlerMethodCollision(RequestMappingInfoHandlerMapping mappings)
    {
        for (RequestMappingInfo mapping : mappings.getHandlerMethods().keySet())
        {
            for (String patternValue : mapping.getPatternValues())
            {
                if (overlaps(patternValue)) return true;
            }
        }
        return false;
    }

    private boolean explicitUrlCollision(AbstractUrlHandlerMapping mappings)
    {
        for (Map.Entry<String, Object> entry : mappings.getHandlerMap().entrySet())
        {
            if (entry.getValue() instanceof ResourceHttpRequestHandler)
            {
                if (dedicatedResourceMapping(entry.getKey())) return true;
            }
            else if (overlaps(entry.getKey())) return true;
        }
        for (Map.Entry<PathPattern, Object> entry : mappings.getPathPatternHandlerMap().entrySet())
        {
            String pattern = entry.getKey().getPatternString();
            if (entry.getValue() instanceof ResourceHttpRequestHandler)
            {
                if (dedicatedResourceMapping(pattern)) return true;
            }
            else if (overlaps(pattern)) return true;
        }
        return false;
    }

    private static boolean dedicatedResourceMapping(String pattern)
    {
        if (pattern.equals("/**") || pattern.equals("/*") || pattern.equals("/{*path}"))
        {
            return false;
        }
        return mayOverlapNamespace(pattern);
    }

    private boolean functionalCollision(RouterFunction<?> router)
    {
        if (router == null) return false;
        CollisionVisitor visitor = new CollisionVisitor();
        router.accept(visitor);
        return visitor.collision;
    }

    private boolean overlaps(String patternValue)
    {
        if (startsWithReservedNamespace(patternValue) || mayOverlapNamespace(patternValue))
        {
            return true;
        }
        try
        {
            PathPattern pattern = parser.parse(patternValue);
            PathContainer root = PathContainer.parsePath(ObservabilityApiPaths.ROOT);
            PathContainer child = PathContainer.parsePath(ObservabilityApiPaths.ROOT + "/reserved-probe");
            return pattern.matches(root) || pattern.matches(child);
        }
        catch (RuntimeException ex)
        {
            return false;
        }
    }

    private static boolean startsWithReservedNamespace(String pattern)
    {
        return pattern.equalsIgnoreCase(ObservabilityApiPaths.ROOT)
                || pattern.length() > ObservabilityApiPaths.ROOT.length()
                && pattern.charAt(ObservabilityApiPaths.ROOT.length()) == '/'
                && pattern.regionMatches(true, 0, ObservabilityApiPaths.ROOT, 0,
                        ObservabilityApiPaths.ROOT.length());
    }

    private static boolean mayOverlapNamespace(String pattern)
    {
        String candidatePath = pattern.startsWith("/") ? pattern.substring(1) : pattern;
        if (candidatePath.isEmpty())
        {
            return false;
        }
        String[] candidate = candidatePath.split("/");
        String[] reserved = ObservabilityApiPaths.ROOT.substring(1).split("/");
        int shared = Math.min(candidate.length, reserved.length);
        for (int index = 0; index < shared; index++)
        {
            String segment = candidate[index];
            boolean dynamic = segment.indexOf('{') >= 0 || segment.indexOf('*') >= 0
                    || segment.indexOf('?') >= 0;
            if (!dynamic && !segment.equalsIgnoreCase(reserved[index]))
            {
                return false;
            }
        }
        if (candidate.length >= reserved.length)
        {
            return true;
        }
        String last = candidate[candidate.length - 1];
        return last.indexOf('*') >= 0 || last.startsWith("{*");
    }

    private PredicatePathVisitor.PathResult pathResult(RequestPredicate predicate)
    {
        PredicatePathVisitor visitor = new PredicatePathVisitor(parser);
        predicate.accept(visitor);
        return visitor.pathResult();
    }

    private final class CollisionVisitor implements RouterFunctions.Visitor
    {
        private final ArrayDeque<PredicatePathVisitor.PathResult> nested = new ArrayDeque<>();
        private boolean collision;

        @Override
        public void startNested(RequestPredicate predicate)
        {
            nested.addLast(pathResult(predicate));
        }

        @Override
        public void endNested(RequestPredicate predicate)
        {
            nested.removeLast();
        }

        @Override
        public void route(RequestPredicate predicate, HandlerFunction<?> handlerFunction)
        {
            PredicatePathVisitor.PathResult leafResult = pathResult(predicate);
            List<String> leaves = leafResult.unconstrained() ? List.of("") : leafResult.paths();
            if (leaves.isEmpty()) return;
            List<String> prefixes = List.of("");
            for (PredicatePathVisitor.PathResult nestedResult : nested)
            {
                if (nestedResult.unclassifiable() && nestedResult.paths().isEmpty()) return;
                List<String> nestedPaths = nestedResult.unconstrained()
                        ? List.of("")
                        : nestedResult.paths();
                List<String> combined = new ArrayList<>(prefixes.size() * nestedPaths.size());
                for (String prefix : prefixes)
                {
                    for (String nestedPath : nestedPaths)
                    {
                        combined.add(prefix + nestedPath);
                    }
                }
                prefixes = combined;
            }
            for (String prefix : prefixes)
            {
                for (String leaf : leaves)
                {
                    String candidate = (prefix + leaf).replace("//", "/");
                    collision |= candidate.isEmpty() || overlaps(candidate);
                }
            }
        }

        @Override
        public void resources(java.util.function.Function<ServerRequest, Optional<Resource>> lookupFunction)
        {
        }

        @Override
        public void attributes(Map<String, Object> attributes)
        {
        }

        @Override
        public void unknown(RouterFunction<?> routerFunction)
        {
        }
    }

    private static final class PredicatePathVisitor implements RequestPredicates.Visitor
    {
        private final PathPatternParser parser;
        private final ArrayDeque<Frame> frames = new ArrayDeque<>();
        private PathResult result;

        private PredicatePathVisitor(PathPatternParser parser)
        {
            this.parser = parser;
        }

        PathResult pathResult()
        {
            return result == null ? PathResult.unclassifiableResult() : result;
        }

        @Override
        public void method(Set<HttpMethod> methods)
        {
            accept(PathResult.unconstrainedResult());
        }

        @Override
        public void path(String pattern)
        {
            accept(PathResult.path(pattern));
        }

        @Override
        public void pathExtension(String extension)
        {
            accept(PathResult.unconstrainedResult());
        }

        @Override
        public void version(String version)
        {
            accept(PathResult.unconstrainedResult());
        }

        @Override
        public void header(String name, String value)
        {
            accept(PathResult.unconstrainedResult());
        }

        @Override
        public void param(String name, String value)
        {
            accept(PathResult.unconstrainedResult());
        }

        @Override
        public void startAnd()
        {
            frames.addLast(new Frame(Operator.AND));
        }

        @Override
        public void and()
        {
            separator();
        }

        @Override
        public void endAnd()
        {
            finish(Operator.AND);
        }

        @Override
        public void startOr()
        {
            frames.addLast(new Frame(Operator.OR));
        }

        @Override
        public void or()
        {
            separator();
        }

        @Override
        public void endOr()
        {
            finish(Operator.OR);
        }

        @Override
        public void startNegate()
        {
            frames.addLast(new Frame(Operator.NEGATE));
        }

        @Override
        public void endNegate()
        {
            if (frames.isEmpty())
            {
                result = PathResult.unclassifiableResult();
                return;
            }
            frames.removeLast();
            accept(PathResult.unclassifiableResult());
        }

        @Override
        public void unknown(RequestPredicate predicate)
        {
            accept(PathResult.unclassifiableResult());
        }

        private void separator()
        {
            if (frames.isEmpty())
            {
                result = PathResult.unclassifiableResult();
                return;
            }
            frames.getLast().rightSide = true;
        }

        private void finish(Operator expected)
        {
            if (frames.isEmpty())
            {
                result = PathResult.unclassifiableResult();
                return;
            }
            Frame frame = frames.removeLast();
            PathResult combined = frame.operator == expected && frame.left != null && frame.right != null
                    ? combine(frame.operator, frame.left, frame.right)
                    : PathResult.unclassifiableResult();
            accept(combined);
        }

        private void accept(PathResult value)
        {
            if (frames.isEmpty())
            {
                result = result == null ? value : PathResult.unclassifiableResult();
                return;
            }
            Frame frame = frames.getLast();
            if (frame.rightSide)
            {
                frame.right = frame.right == null ? value : PathResult.unclassifiableResult();
            }
            else
            {
                frame.left = frame.left == null ? value : PathResult.unclassifiableResult();
            }
        }

        private PathResult combine(Operator operator, PathResult left, PathResult right)
        {
            if (operator == Operator.OR)
            {
                if (left.unconstrained() || right.unconstrained())
                {
                    return PathResult.unconstrainedResult();
                }
                List<String> alternatives = new ArrayList<>(left.paths());
                right.paths().stream().filter(path -> !alternatives.contains(path)).forEach(alternatives::add);
                return new PathResult(left.unclassifiable() || right.unclassifiable(), false,
                        List.copyOf(alternatives));
            }
            if (left.unclassifiable() && left.paths().isEmpty())
                return PathResult.unclassifiableResult();
            if (right.unclassifiable() && right.paths().isEmpty())
                return PathResult.unclassifiableResult();
            if (left.unconstrained()) return right;
            if (right.unconstrained()) return left;
            List<String> intersections = new ArrayList<>();
            for (String leftPath : left.paths())
            {
                for (String rightPath : right.paths())
                {
                    addKnownIntersection(leftPath, rightPath, intersections);
                }
            }
            return intersections.isEmpty()
                    ? PathResult.unclassifiableResult()
                    : new PathResult(left.unclassifiable() || right.unclassifiable(), false,
                            List.copyOf(intersections));
        }

        private void addKnownIntersection(String left, String right, List<String> intersections)
        {
            if (left.equals(right))
            {
                addDistinct(intersections, left);
                return;
            }
            try
            {
                PathPattern leftPattern = parser.parse(left);
                PathPattern rightPattern = parser.parse(right);
                addMatchingLiteral(left, rightPattern, intersections);
                addMatchingLiteral(right, leftPattern, intersections);
                addSharedWitness(ObservabilityApiPaths.ROOT, leftPattern, rightPattern, intersections);
                addSharedWitness(ObservabilityApiPaths.ROOT + "/reserved-probe",
                        leftPattern, rightPattern, intersections);
                addPatternWitness(left, leftPattern, rightPattern, intersections);
                addPatternWitness(right, leftPattern, rightPattern, intersections);
                addAlignedSegmentWitness(left, right, leftPattern, rightPattern, intersections);
            }
            catch (RuntimeException ex)
            {
                // An invalid or otherwise unclassifiable intersection supplies no positive path evidence.
            }
        }

        private static void addMatchingLiteral(String candidate, PathPattern pattern,
                List<String> intersections)
        {
            if (candidate.indexOf('{') < 0 && candidate.indexOf('*') < 0
                    && candidate.indexOf('?') < 0
                    && pattern.matches(PathContainer.parsePath(candidate)))
            {
                addDistinct(intersections, candidate);
            }
        }

        private static void addSharedWitness(String candidate, PathPattern left, PathPattern right,
                List<String> intersections)
        {
            PathContainer path = PathContainer.parsePath(candidate);
            if (left.matches(path) && right.matches(path))
            {
                addDistinct(intersections, candidate);
            }
        }

        private static void addAlignedSegmentWitness(String leftValue, String rightValue,
                PathPattern left, PathPattern right, List<String> intersections)
        {
            String[] leftSegments = segments(leftValue);
            String[] rightSegments = segments(rightValue);
            if (leftSegments.length != rightSegments.length) return;

            String[] reservedSegments = segments(ObservabilityApiPaths.ROOT);
            StringBuilder candidate = new StringBuilder();
            for (int index = 0; index < leftSegments.length; index++)
            {
                String leftSegment = leftSegments[index];
                String rightSegment = rightSegments[index];
                String segment;
                if (!dynamicSegment(leftSegment) && !dynamicSegment(rightSegment))
                {
                    if (!leftSegment.equals(rightSegment)) return;
                    segment = leftSegment;
                }
                else if (!dynamicSegment(leftSegment))
                {
                    segment = leftSegment;
                }
                else if (!dynamicSegment(rightSegment))
                {
                    segment = rightSegment;
                }
                else
                {
                    segment = index < reservedSegments.length
                            ? reservedSegments[index]
                            : "reserved-probe";
                }
                candidate.append('/').append(segment);
            }
            String candidateValue = candidate.isEmpty() ? "/" : candidate.toString();
            PathContainer path = PathContainer.parsePath(candidateValue);
            if (startsWithReservedNamespace(candidateValue)
                    && left.matches(path) && right.matches(path))
            {
                addDistinct(intersections, candidateValue);
            }
        }

        private static void addPatternWitness(String pattern, PathPattern left, PathPattern right,
                List<String> intersections)
        {
            String[] patternSegments = segments(pattern);
            String[] reservedSegments = segments(ObservabilityApiPaths.ROOT);
            StringBuilder candidate = new StringBuilder();
            for (int index = 0; index < patternSegments.length; index++)
            {
                String segment = patternSegments[index];
                if (dynamicSegment(segment))
                {
                    segment = index < reservedSegments.length
                            ? reservedSegments[index]
                            : "reserved-probe";
                }
                candidate.append('/').append(segment);
            }
            String candidateValue = candidate.isEmpty() ? "/" : candidate.toString();
            PathContainer path = PathContainer.parsePath(candidateValue);
            if (startsWithReservedNamespace(candidateValue)
                    && left.matches(path) && right.matches(path))
            {
                addDistinct(intersections, candidateValue);
            }
        }

        private static String[] segments(String pattern)
        {
            String value = pattern.startsWith("/") ? pattern.substring(1) : pattern;
            return value.isEmpty() ? new String[0] : value.split("/");
        }

        private static boolean dynamicSegment(String segment)
        {
            return segment.indexOf('{') >= 0 || segment.indexOf('*') >= 0
                    || segment.indexOf('?') >= 0;
        }

        private static void addDistinct(List<String> intersections, String candidate)
        {
            if (!intersections.contains(candidate)) intersections.add(candidate);
        }

        private enum Operator { AND, OR, NEGATE }

        private static final class Frame
        {
            private final Operator operator;
            private PathResult left;
            private PathResult right;
            private boolean rightSide;

            private Frame(Operator operator)
            {
                this.operator = operator;
            }
        }

        private record PathResult(boolean unclassifiable, boolean unconstrained, List<String> paths)
        {
            private static PathResult path(String value)
            {
                return new PathResult(false, false, List.of(value));
            }

            private static PathResult unconstrainedResult()
            {
                return new PathResult(false, true, List.of());
            }

            private static PathResult unclassifiableResult()
            {
                return new PathResult(true, false, List.of());
            }
        }
    }
}
