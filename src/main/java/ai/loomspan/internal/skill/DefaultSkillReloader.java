package ai.loomspan.internal.skill;

import ai.loomspan.api.PreparedSkillUpdate;
import ai.loomspan.api.SkillCatalog;
import ai.loomspan.api.SkillReloadException;
import ai.loomspan.api.SkillReloader;
import ai.loomspan.internal.core.FrameworkExecutionLifecycle;

import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;

/** Framework-owned publication coordinator. Candidate ownership is carried by identity, not a registry. */
public final class DefaultSkillReloader implements SkillReloader
{
    private final SkillGenerationManager generations;
    private final FrameworkExecutionLifecycle lifecycle;
    private final Object preparationLock = new Object();
    private final Object publicationLock = new Object();
    private final Object owner = new Object();

    public DefaultSkillReloader(SkillGenerationManager generations, FrameworkExecutionLifecycle lifecycle)
    {
        this.generations = Objects.requireNonNull(generations, "generations must not be null");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle must not be null");
    }

    @Override
    public PreparedSkillUpdate prepare()
    {
        synchronized (preparationLock)
        {
            String baseId = readWhileOpen();
            final SkillGeneration candidate;
            try { candidate = generations.prepare(); }
            catch (RuntimeException ex) { throw new SkillReloadException("Failed to prepare skill generation", ex); }
            checkOpen();
            return new Candidate(owner, baseId, candidate);
        }
    }

    @Override
    public void publish(PreparedSkillUpdate update)
    {
        Objects.requireNonNull(update, "update must not be null");
        synchronized (publicationLock)
        {
            if (!(update instanceof Candidate candidate) || candidate.owner != owner)
                throw new SkillReloadException("Skill update belongs to another framework instance or was not prepared by Loomspan");
            try
            {
                lifecycle.whileAdmissionOpen(() -> {
                    if (candidate.published)
                        throw new SkillReloadException("Skill update was already published");
                    if (!generations.active().id().equals(candidate.baseId))
                        throw new SkillReloadException("Skill update is stale; active generation changed since preparation");
                    generations.activate(candidate.generation);
                    candidate.published = true;
                });
            }
            catch (RejectedExecutionException ex)
            {
                throw new SkillReloadException("Cannot publish skill update after shutdown began", ex);
            }
        }
    }

    @Override
    public SkillCatalog snapshot() { return generations.active().skillCatalog(); }

    private String readWhileOpen()
    {
        final String[] result = new String[1];
        try { lifecycle.whileAdmissionOpen(() -> result[0] = generations.active().id()); }
        catch (RejectedExecutionException ex)
        { throw new SkillReloadException("Cannot prepare skill update after shutdown began", ex); }
        return result[0];
    }

    private void checkOpen()
    {
        try { lifecycle.whileAdmissionOpen(() -> { }); }
        catch (RejectedExecutionException ex)
        { throw new SkillReloadException("Shutdown began during skill preparation", ex); }
    }

    private static final class Candidate implements PreparedSkillUpdate
    {
        private final Object owner;
        private final String baseId;
        private final SkillGeneration generation;
        private boolean published;

        private Candidate(Object owner, String baseId, SkillGeneration generation)
        {
            this.owner = owner;
            this.baseId = baseId;
            this.generation = generation;
        }

        @Override public String generationId() { return generation.id(); }
        @Override public SkillCatalog snapshot() { return generation.skillCatalog(); }
    }
}
