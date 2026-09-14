package ai.loomspan.internal.skillapi;

import ai.loomspan.api.AdmittedSkillInvocation;
import ai.loomspan.api.SkillException;
import ai.loomspan.api.SkillExecutionView;
import ai.loomspan.api.SkillInvocationHandoff;
import ai.loomspan.internal.core.FrameworkExecutionLifecycle;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Internal implementation of the application-facing atomic invocation handoff. */
public final class DefaultSkillInvocationHandoff implements SkillInvocationHandoff
{
    private final DefaultSkillTemplate template;
    private final FrameworkExecutionLifecycle lifecycle;

    public DefaultSkillInvocationHandoff(DefaultSkillTemplate template, FrameworkExecutionLifecycle lifecycle)
    {
        this.template = Objects.requireNonNull(template, "template must not be null");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle must not be null");
    }

    @Override
    public AdmittedSkillInvocation handoff(String skillName, Object input)
    {
        return admit(skillName, template.prepareObject(skillName, input));
    }

    @Override
    public AdmittedSkillInvocation handoff(String skillName, Map<String, Object> input)
    {
        return admit(skillName, template.prepareMap(skillName, input));
    }

    private AdmittedSkillInvocation admit(String skillName, DefaultSkillTemplate.PreparedInput prepared)
    {
        try
        {
            Authentication authentication = template.currentAuthentication();
            return new DefaultAdmittedSkillInvocation(
                    skillName, prepared, authentication, lifecycle.admitRoot());
        }
        catch (AccessDeniedException | SkillException ex)
        {
            throw ex;
        }
        catch (RuntimeException ex)
        {
            throw new SkillException("Skill '" + skillName + "' execution failed.", ex);
        }
    }

    private final class DefaultAdmittedSkillInvocation implements AdmittedSkillInvocation
    {
        private final String skillName;
        private final DefaultSkillTemplate.PreparedInput prepared;
        private final Authentication authentication;
        private final FrameworkExecutionLifecycle.AdmittedRoot root;

        private DefaultAdmittedSkillInvocation(String skillName,
                DefaultSkillTemplate.PreparedInput prepared,
                Authentication authentication,
                FrameworkExecutionLifecycle.AdmittedRoot root)
        {
            this.skillName = skillName;
            this.prepared = prepared;
            this.authentication = authentication;
            this.root = root;
        }

        @Override
        public String invoke()
        {
            return invoke(null);
        }

        @Override
        public String invoke(Consumer<SkillExecutionView> observer)
        {
            return template.invokePrepared(skillName, prepared, observer, authentication, root);
        }

        @Override
        public void release()
        {
            root.close();
        }
    }
}
