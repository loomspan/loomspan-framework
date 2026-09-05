package ai.loomspan.internal.observability.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.function.support.RouterFunctionMapping;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;

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
    void failsClosedForFunctionalResourceLookup()
    {
        RouterFunctionMapping functional = new RouterFunctionMapping();
        functional.setRouterFunction(RouterFunctions.resources(
                "/assets/**",
                new org.springframework.core.io.ClassPathResource("static/")));

        assertThat(new ObservabilityRouteCollisionDetector(List.of(functional)).hasCollision()).isTrue();
    }

    @Test
    void failsClosedForUnclassifiableHandlerMapping()
    {
        HandlerMapping unclassifiable = request -> null;

        assertThat(new ObservabilityRouteCollisionDetector(List.of(unclassifiable)).hasCollision()).isTrue();
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
}
