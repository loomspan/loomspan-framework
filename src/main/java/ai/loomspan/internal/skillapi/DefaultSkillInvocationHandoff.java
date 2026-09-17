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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

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
                    skillName, prepared, authentication, lifecycle.admitRoot(prepared.lease()));
        }
        catch (AccessDeniedException | SkillException ex)
        {
            prepared.lease().close();
            throw ex;
        }
        catch (RuntimeException ex)
        {
            prepared.lease().close();
            throw new SkillException("Skill '" + skillName + "' execution failed.", ex);
        }
        catch (Error ex)
        {
            prepared.lease().close();
            throw ex;
        }
    }

    private final class DefaultAdmittedSkillInvocation implements AdmittedSkillInvocation
    {
        private final AtomicReference<Payload> payload;
        private final String skillName;
        private final FrameworkExecutionLifecycle.AdmittedRoot root;

        private DefaultAdmittedSkillInvocation(String skillName,
                DefaultSkillTemplate.PreparedInput prepared,
                Authentication authentication,
                FrameworkExecutionLifecycle.AdmittedRoot root)
        {
            this.skillName = skillName;
            this.payload = new AtomicReference<>(new Payload(skillName, prepared, authentication));
            this.root = root;
            root.onPendingTermination(() -> payload.set(null));
        }

        @Override
        public String invoke()
        {
            return invoke(null);
        }

        @Override
        public String invoke(Consumer<SkillExecutionView> observer)
        {
            Payload claimed = payload.getAndSet(null);
            if (claimed == null)
                throw new SkillException("Skill '" + skillName + "' execution failed.",
                        new RejectedExecutionException("Loomspan invocation admission is no longer executable"));
            return template.invokePrepared(claimed.skillName(), claimed.prepared(), observer,
                    claimed.authentication(), root);
        }

        @Override
        public void release()
        {
            payload.set(null);
            root.close();
        }

        private record Payload(String skillName, DefaultSkillTemplate.PreparedInput prepared,
                Authentication authentication) {}
    }
}
