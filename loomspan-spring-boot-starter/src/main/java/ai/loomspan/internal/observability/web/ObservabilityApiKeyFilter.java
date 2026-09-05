package ai.loomspan.internal.observability.web;

import ai.loomspan.internal.observability.ObservabilityActivationCoordinator;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class ObservabilityApiKeyFilter implements Filter
{
    public static final String API_KEY_HEADER = "X-loomspan-Api-Key";
    public static final String INSTANCE_HEADER = "X-loomspan-Instance-Id";
    private static final Logger LOGGER = LoggerFactory.getLogger(ObservabilityApiKeyFilter.class);

    private final ObservabilityActivationCoordinator activation;
    private final ObservabilityJsonCodec json;
    private final ObservabilityProblemMapper problemMapper;

    public ObservabilityApiKeyFilter(
            ObservabilityActivationCoordinator activation,
            ObservabilityJsonCodec json,
            ObservabilityProblemMapper problemMapper)
    {
        this.activation = activation;
        this.json = json;
        this.problemMapper = problemMapper;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException
    {
        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)
                || !activation.enabled())
        {
            chain.doFilter(request, response);
            return;
        }
        httpResponse.setHeader("Cache-Control", "no-store");
        SecurityContext previous = SecurityContextHolder.getContext();
        try
        {
            String presented = presentedKey(httpRequest);
            String configured = activation.runtime().orElseThrow().configuration().getAuth().getApiKey();
            if (!valid(presented) || !MessageDigest.isEqual(
                    presented.getBytes(StandardCharsets.UTF_8), configured.getBytes(StandardCharsets.UTF_8)))
            {
                writeProblem(httpResponse, new ObservabilityProblem(
                        401, ObservabilityProblem.Code.LOOMSPAN_API_KEY_REJECTED, "loomspan API key was rejected"), false);
                return;
            }
            var authentication = new ObservabilityOperatorAuthentication();
            SecurityContextHolder.setContext(new SecurityContextImpl(authentication));
            httpResponse.setHeader(INSTANCE_HEADER, activation.runtime().orElseThrow().instanceId().toString());
            LOGGER.debug("Authenticated Loomspan observability operator");
            if (!"GET".equals(httpRequest.getMethod()))
            {
                writeProblem(httpResponse, new ObservabilityProblem(
                        400, ObservabilityProblem.Code.INVALID_REQUEST,
                        "The request method or shape is not supported"), true);
                return;
            }
            try
            {
                chain.doFilter(request, response);
            }
            catch (Exception failure)
            {
                if (httpResponse.isCommitted())
                {
                    if (failure instanceof IOException io) throw io;
                    if (failure instanceof ServletException servlet) throw servlet;
                    throw new ServletException(failure);
                }
                ObservabilityProblem problem = problemMapper.map(failure);
                if (problem.code() == ObservabilityProblem.Code.APPLICATION_ERROR)
                {
                    LOGGER.warn(
                            "loomspan observability request failed exceptionClass={}",
                            failure.getClass().getName());
                }
                writeProblem(httpResponse, problem, true);
            }
        }
        finally
        {
            SecurityContextHolder.setContext(previous);
        }
    }

    private static String presentedKey(HttpServletRequest request)
    {
        var values = request.getHeaders(API_KEY_HEADER);
        if (values == null || !values.hasMoreElements())
        {
            return null;
        }
        String value = values.nextElement();
        return values.hasMoreElements() ? null : value;
    }

    private static boolean valid(String value)
    {
        if (value == null || value.length() < 32 || value.length() > 512)
        {
            return false;
        }
        for (int i = 0; i < value.length(); i++)
        {
            char character = value.charAt(i);
            if (character < 0x21 || character > 0x7e)
            {
                return false;
            }
        }
        return true;
    }

    private void writeProblem(HttpServletResponse response, ObservabilityProblem problem, boolean authenticated)
            throws IOException
    {
        response.resetBuffer();
        response.setStatus(problem.status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-store");
        if (!authenticated)
        {
            response.setHeader(INSTANCE_HEADER, null);
        }
        response.getOutputStream().write(json.write(problem));
    }
}
