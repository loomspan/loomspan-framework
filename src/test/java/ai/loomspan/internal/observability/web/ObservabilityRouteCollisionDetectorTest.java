package ai.loomspan.internal.observability.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.function.support.RouterFunctionMapping;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;
import org.springframework.web.util.pattern.PathPatternParser;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.web.servlet.function.RequestPredicates.GET;

class ObservabilityRouteCollisionDetectorTest
{
    @Test
    void detectsExactVariableWildcardAndCatchAllAnnotatedMappings()
    {
        for (String pattern : List.of(
                ObservabilityApiPaths.ROOT,
                "/{root}/observability/v1",
                "/_loomspan/{scope}/v1/instance",
                ObservabilityApiPaths.ROOT + "/*",
                "/{*path}"))
        {
            RequestMappingHandlerMapping mappings = annotated(pattern);
            assertThat(new ObservabilityRouteCollisionDetector(List.of(mappings)).hasCollision())
                    .as(pattern)
                    .isTrue();
        }
    }

    @Test
    void detectsCaseInsensitiveHostMappingOverlap()
    {
        PathPatternParser parser = new PathPatternParser();
        parser.setCaseSensitive(false);
        RequestMappingInfo.BuilderConfiguration options = new RequestMappingInfo.BuilderConfiguration();
        options.setPatternParser(parser);
        RequestMappingInfo info = RequestMappingInfo
                .paths("/_loomspan/OBSERVABILITY/V1/instance")
                .methods(RequestMethod.GET)
                .options(options)
                .build();
        RequestMappingHandlerMapping mappings = new RequestMappingHandlerMapping();
        mappings.registerMapping(info, new Object(), Object.class.getMethods()[0]);

        assertThat(info.getPathPatternsCondition().getPatterns().iterator().next()
                .matches(org.springframework.http.server.PathContainer.parsePath(ObservabilityApiPaths.INSTANCE)))
                .isTrue();
        assertThat(new ObservabilityRouteCollisionDetector(List.of(mappings)).hasCollision()).isTrue();
    }

    @Test
    void detectsFunctionalRouterAndExplicitUrlHandlerOverlap()
    {
        RouterFunctionMapping functional = new RouterFunctionMapping();
        functional.setRouterFunction(RouterFunctions.route(
                GET(ObservabilityApiPaths.ROOT + "/activity"),
                request -> ServerResponse.ok().build()));

        SimpleUrlHandlerMapping explicit = new SimpleUrlHandlerMapping();
        explicit.registerHandler(ObservabilityApiPaths.ROOT + "/artifacts/**", new Object());

        assertThat(new ObservabilityRouteCollisionDetector(List.of(functional)).hasCollision()).isTrue();
        assertThat(new ObservabilityRouteCollisionDetector(List.of(explicit)).hasCollision()).isTrue();
    }

    @Test
    void detectsReservedPathInCompoundFunctionalPredicate()
    {
        RouterFunctionMapping functional = new RouterFunctionMapping();
        functional.setRouterFunction(RouterFunctions.route(
                GET("/health").or(GET(ObservabilityApiPaths.ROOT + "/activity")),
                request -> ServerResponse.ok().build()));

        assertThat(new ObservabilityRouteCollisionDetector(List.of(functional)).hasCollision()).isTrue();
    }

    @Test
    void treatsFunctionalPredicateWithoutPathConstraintAsCollision()
    {
        RouterFunctionMapping functional = new RouterFunctionMapping();
        functional.setRouterFunction(RouterFunctions.route(
                org.springframework.web.servlet.function.RequestPredicates.accept(MediaType.APPLICATION_JSON),
                request -> ServerResponse.ok().build()));

        assertThat(new ObservabilityRouteCollisionDetector(List.of(functional)).hasCollision()).isTrue();
    }

    @Test
    void ignoresFunctionalResourceLookupWithoutExposedPathEvidence()
    {
        RouterFunctionMapping functional = new RouterFunctionMapping();
        functional.setRouterFunction(RouterFunctions.resources(
                "/assets/**",
                new org.springframework.core.io.ClassPathResource("static/")));

        assertThat(new ObservabilityRouteCollisionDetector(List.of(functional)).hasCollision()).isFalse();
    }

    @Test
    void ignoresUnclassifiableHandlerMappingWithoutPathEvidence()
    {
        HandlerMapping unclassifiable = request -> null;

        assertThat(new ObservabilityRouteCollisionDetector(List.of(unclassifiable)).hasCollision()).isFalse();
    }

    @Test
    void inspectsGeneralRequestMappingInfoHandlerMappings()
    {
        GeneralRequestMappingHandlerMapping empty = new GeneralRequestMappingHandlerMapping();
        assertThat(new ObservabilityRouteCollisionDetector(List.of(empty)).hasCollision()).isFalse();

        GeneralRequestMappingHandlerMapping unrelated = general("/health");
        assertThat(new ObservabilityRouteCollisionDetector(List.of(unrelated)).hasCollision()).isFalse();

        for (String pattern : List.of(
                ObservabilityApiPaths.ROOT,
                ObservabilityApiPaths.ROOT + "/{id}",
                "/**"))
        {
            assertThat(new ObservabilityRouteCollisionDetector(List.of(general(pattern))).hasCollision())
                    .as(pattern)
                    .isTrue();
        }
    }

    @Test
    void classifiesFunctionalRoutesOnlyFromPositivePathEvidence()
    {
        RequestPredicate unknownPredicate = new RequestPredicate()
        {
            @Override
            public boolean test(ServerRequest request)
            {
                return false;
            }

            @Override
            public void accept(org.springframework.web.servlet.function.RequestPredicates.Visitor visitor)
            {
                visitor.unknown(this);
            }
        };
        RouterFunctionMapping unknownLeaf = new RouterFunctionMapping();
        unknownLeaf.setRouterFunction(RouterFunctions.route(
                unknownPredicate,
                request -> ServerResponse.ok().build()));
        assertThat(new ObservabilityRouteCollisionDetector(List.of(unknownLeaf)).hasCollision()).isFalse();

        RouterFunction<ServerResponse> unknownRouter = new RouterFunction<>()
        {
            @Override
            public Optional<org.springframework.web.servlet.function.HandlerFunction<ServerResponse>> route(
                    ServerRequest request)
            {
                return Optional.empty();
            }

            @Override
            public void accept(RouterFunctions.Visitor visitor)
            {
                visitor.unknown(this);
            }
        };
        RouterFunctionMapping unknownCallback = new RouterFunctionMapping();
        unknownCallback.setRouterFunction(unknownRouter);
        assertThat(new ObservabilityRouteCollisionDetector(List.of(unknownCallback)).hasCollision()).isFalse();

        RouterFunctionMapping unconstrainedNested = new RouterFunctionMapping();
        unconstrainedNested.setRouterFunction(RouterFunctions.nest(
                org.springframework.web.servlet.function.RequestPredicates.accept(MediaType.APPLICATION_JSON),
                RouterFunctions.route(GET("/health"), request -> ServerResponse.ok().build())));
        assertThat(new ObservabilityRouteCollisionDetector(List.of(unconstrainedNested)).hasCollision()).isFalse();

        RouterFunctionMapping knownAlternative = new RouterFunctionMapping();
        knownAlternative.setRouterFunction(RouterFunctions.route(
                GET(ObservabilityApiPaths.INSTANCE).or(unknownPredicate),
                request -> ServerResponse.ok().build()));
        assertThat(new ObservabilityRouteCollisionDetector(List.of(knownAlternative)).hasCollision()).isTrue();

        RouterFunctionMapping unknownConjunction = new RouterFunctionMapping();
        unknownConjunction.setRouterFunction(RouterFunctions.route(
                unknownPredicate.and(GET(ObservabilityApiPaths.INSTANCE)),
                request -> ServerResponse.ok().build()));
        assertThat(new ObservabilityRouteCollisionDetector(List.of(unknownConjunction)).hasCollision())
                .isFalse();

        RouterFunctionMapping broadAndExact = new RouterFunctionMapping();
        broadAndExact.setRouterFunction(RouterFunctions.route(
                org.springframework.web.servlet.function.RequestPredicates.path("/**")
                        .and(GET(ObservabilityApiPaths.ROOT)),
                request -> ServerResponse.ok().build()));
        assertThat(new ObservabilityRouteCollisionDetector(List.of(broadAndExact)).hasCollision()).isTrue();

        RouterFunctionMapping intersectingDynamicPaths = new RouterFunctionMapping();
        intersectingDynamicPaths.setRouterFunction(RouterFunctions.route(
                org.springframework.web.servlet.function.RequestPredicates.path(
                                ObservabilityApiPaths.ROOT + "/{collection}/{id}")
                        .and(org.springframework.web.servlet.function.RequestPredicates.path(
                                ObservabilityApiPaths.ROOT + "/skills/{name}")),
                request -> ServerResponse.ok().build()));
        assertThat(new ObservabilityRouteCollisionDetector(List.of(intersectingDynamicPaths)).hasCollision())
                .isTrue();

        RouterFunctionMapping broadAndDynamic = new RouterFunctionMapping();
        broadAndDynamic.setRouterFunction(RouterFunctions.route(
                org.springframework.web.servlet.function.RequestPredicates.path(
                                ObservabilityApiPaths.ROOT + "/{*path}")
                        .and(org.springframework.web.servlet.function.RequestPredicates.path(
                                ObservabilityApiPaths.ROOT + "/skills/{name}")),
                request -> ServerResponse.ok().build()));
        assertThat(new ObservabilityRouteCollisionDetector(List.of(broadAndDynamic)).hasCollision())
                .isTrue();
    }

    @Test
    void doesNotInferCollisionOnlyFromPatternParseFailure()
    {
        SimpleUrlHandlerMapping explicit = new SimpleUrlHandlerMapping();
        explicit.setPatternParser(null);
        explicit.registerHandler("/health/{broken", new Object());

        assertThat(new ObservabilityRouteCollisionDetector(List.of(explicit)).hasCollision()).isFalse();
    }

    @Test
    void ignoresUnrelatedApplicationRoutesAndResourceFallbacks()
    {
        RequestMappingHandlerMapping annotated = annotated("/orders/{id}");
        RequestMappingHandlerMapping variable = annotated("/{tenant}/health");
        RequestMappingHandlerMapping root = annotated("/");
        RouterFunctionMapping functional = new RouterFunctionMapping();
        functional.setRouterFunction(RouterFunctions.route(
                GET("/health"),
                request -> ServerResponse.ok().build()));
        SimpleUrlHandlerMapping explicit = new SimpleUrlHandlerMapping();
        explicit.registerHandler("/assets/**", new Object());

        assertThat(new ObservabilityRouteCollisionDetector(
                List.of(annotated, variable, root, functional, explicit)).hasCollision()).isFalse();
    }

    @Test
    void detectsApplicationResourceHandlerInsideReservedNamespace()
    {
        SimpleUrlHandlerMapping explicit = new SimpleUrlHandlerMapping();
        explicit.registerHandler(
                ObservabilityApiPaths.ROOT + "/**",
                new ResourceHttpRequestHandler());

        assertThat(new ObservabilityRouteCollisionDetector(List.of(explicit)).hasCollision()).isTrue();

        SimpleUrlHandlerMapping parent = new SimpleUrlHandlerMapping();
        parent.registerHandler("/_loomspan/**", new ResourceHttpRequestHandler());
        assertThat(new ObservabilityRouteCollisionDetector(List.of(parent)).hasCollision()).isTrue();
    }

    private static RequestMappingHandlerMapping annotated(String pattern)
    {
        RequestMappingHandlerMapping mappings = new RequestMappingHandlerMapping();
        RequestMappingInfo info = RequestMappingInfo.paths(pattern).methods(RequestMethod.GET).build();
        mappings.registerMapping(info, new Object(), Object.class.getMethods()[0]);
        return mappings;
    }

    private static GeneralRequestMappingHandlerMapping general(String pattern)
    {
        GeneralRequestMappingHandlerMapping mappings = new GeneralRequestMappingHandlerMapping();
        RequestMappingInfo info = RequestMappingInfo.paths(pattern).methods(RequestMethod.GET).build();
        mappings.registerMapping(info, new Object(), Object.class.getMethods()[0]);
        return mappings;
    }

    private static final class GeneralRequestMappingHandlerMapping extends RequestMappingInfoHandlerMapping
    {
        @Override
        protected boolean isHandler(Class<?> beanType)
        {
            return false;
        }

        @Override
        protected RequestMappingInfo getMappingForMethod(Method method, Class<?> handlerType)
        {
            return null;
        }
    }
}
