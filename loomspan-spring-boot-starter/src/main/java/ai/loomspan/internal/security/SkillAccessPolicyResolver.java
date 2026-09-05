package ai.loomspan.internal.security;

import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.security.core.annotation.SecurityAnnotationScanner;
import org.springframework.security.core.annotation.SecurityAnnotationScanners;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;

public final class SkillAccessPolicyResolver
{
    private final SecurityAnnotationScanner<?> scanner = SecurityAnnotationScanners.requireUnique(
            List.of(DenyAll.class, PermitAll.class, RolesAllowed.class));

    public SkillAccessPolicy resolve(Method method, Class<?> targetClass)
    {
        Annotation annotation = scanner.scan(method, targetClass);
        if (annotation instanceof DenyAll) return SkillAccessPolicy.denied();
        if (annotation instanceof PermitAll) return SkillAccessPolicy.permitAll();
        if (annotation instanceof RolesAllowed roles) return SkillAccessPolicy.roles(List.of(roles.value()));
        return SkillAccessPolicy.unrestricted();
    }
}
