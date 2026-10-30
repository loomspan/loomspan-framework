package ai.loomspan.internal.skill;

import ai.loomspan.internal.core.SkillIdentity;

import ai.loomspan.api.PreparedSkillUpdate;
import ai.loomspan.api.NamespaceVisibility;
import ai.loomspan.api.ExecutionConfiguration;
import ai.loomspan.api.SkillCatalog;
import ai.loomspan.api.SkillReloadException;
import ai.loomspan.api.SkillReloader;
import ai.loomspan.api.SkillDocument;
import ai.loomspan.api.SkillValidationResult;
import ai.loomspan.internal.core.FrameworkExecutionLifecycle;

import java.util.Objects;
import java.util.Collection;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/** Framework-owned publication coordinator. Candidate ownership is carried by identity, not a registry. */
public final class DefaultSkillReloader implements SkillReloader
{
    private final SkillGenerationManager generations;
    private final FrameworkExecutionLifecycle lifecycle;
    private final Object owner = new Object();

    public DefaultSkillReloader(SkillGenerationManager generations, FrameworkExecutionLifecycle lifecycle)
    {
        this.generations = Objects.requireNonNull(generations, "generations must not be null");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle must not be null");
        generations.deliveryEnabled(lifecycle::isAdmissionOpen);
    }

    @Override public SkillValidationResult validate(String namespace, Collection<SkillDocument> documents,
            ExecutionConfiguration configuration) {
        return validate(namespace, documents, configuration, NamespaceVisibility.PRIVATE);
    }

    @Override public SkillValidationResult validate(String namespace, Collection<SkillDocument> documents,
            ExecutionConfiguration configuration, NamespaceVisibility visibility) {
        String target = SkillIdentity.normalizeNamespace(namespace);
        Objects.requireNonNull(configuration, "configuration must not be null");
        Objects.requireNonNull(visibility, "visibility must not be null");
        var supplied = new ArrayList<>(Objects.requireNonNull(documents, "documents must not be null"));
        return generations.validate(readWhileOpen(target), supplied, configuration, visibility);
    }

    @Override public PreparedSkillUpdate prepare(String namespace, Collection<SkillDocument> documents,
            ExecutionConfiguration configuration) {
        return prepareInternal(namespace, documents, configuration, NamespaceVisibility.PRIVATE, null);
    }
    @Override public PreparedSkillUpdate prepare(String namespace, Collection<SkillDocument> documents,
            ExecutionConfiguration configuration, NamespaceVisibility visibility) {
        return prepareInternal(namespace, documents, configuration, visibility, null);
    }
    @Override public PreparedSkillUpdate prepare(String namespace, Collection<SkillDocument> documents,
            ExecutionConfiguration configuration, Map<String, String> credentialValues) {
        return prepare(namespace, documents, configuration, NamespaceVisibility.PRIVATE, credentialValues);
    }
    @Override public PreparedSkillUpdate prepare(String namespace, Collection<SkillDocument> documents,
            ExecutionConfiguration configuration, NamespaceVisibility visibility, Map<String, String> credentialValues) {
        return prepareInternal(namespace, documents, configuration, visibility,
                new LinkedHashMap<>(Objects.requireNonNull(credentialValues, "credentialValues must not be null")));
    }

    private PreparedSkillUpdate prepareInternal(String namespace, Collection<SkillDocument> documents,
            ExecutionConfiguration configuration, NamespaceVisibility visibility, Map<String, String> credentials) {
        String target = SkillIdentity.normalizeNamespace(namespace);
        Objects.requireNonNull(configuration, "configuration must not be null");
        Objects.requireNonNull(visibility, "visibility must not be null");
        var supplied = new ArrayList<>(Objects.requireNonNull(documents, "documents must not be null"));
        var view = readWhileOpen(target);
        final SkillGeneration candidate;
        try { candidate = generations.prepare(view, supplied, configuration, visibility, credentials); }
        catch (RuntimeException ex) { throw new SkillReloadException("Failed to prepare skill generation: " + ex.getMessage(), ex); }
        try { checkOpen(); }
        catch (RuntimeException | Error ex) {
            try { candidate.close(); } catch (RuntimeException failure) { ex.addSuppressed(failure); }
            throw ex;
        }
        return new Candidate(owner, view.baseId(), candidate);
    }

    @Override
    public void publish(PreparedSkillUpdate update)
    {
        Objects.requireNonNull(update, "update must not be null");
        SkillGenerationManager.Retirement[] retirement = new SkillGenerationManager.Retirement[1];
        if (!(update instanceof Candidate candidate) || candidate.owner != owner)
            throw new SkillReloadException("Skill update belongs to another framework instance or was not prepared by Loomspan");
        try
        {
            synchronized (candidate)
                {
                    lifecycle.whileAdmissionOpen(() -> {
                        if (candidate.published)
                            throw new SkillReloadException("Skill update was already published");
                        if (candidate.closed)
                            throw new SkillReloadException("Skill update was closed");
                        retirement[0] = generations.publish(candidate.generation, candidate.baseId);
                        candidate.published = true;
                    });
            }
        }
        catch (RejectedExecutionException ex)
        {
            closeRejected(candidate, ex);
            throw new SkillReloadException("Cannot publish skill update after shutdown began", ex);
        }
        catch (SkillReloadException ex)
        {
            closeRejected(candidate, ex);
            throw ex;
        }
        SkillGenerationManager.dispatch(retirement[0]);
    }

    @Override
    public AutoCloseable onGenerationRetired(Consumer<String> listener)
    {
        return generations.onGenerationRetired(listener);
    }

    @Override public SkillCatalog snapshot(String namespace) {
        return generations.active(SkillIdentity.normalizeNamespace(namespace)).skillCatalog();
    }

    @Override public void remove(String namespace, String expectedGenerationId) {
        String target = SkillIdentity.normalizeNamespace(namespace);
        SkillGenerationManager.Retirement[] retirement = new SkillGenerationManager.Retirement[1];
        try { lifecycle.whileAdmissionOpen(() -> retirement[0] = generations.remove(target, expectedGenerationId)); }
        catch (RejectedExecutionException ex) { throw new SkillReloadException("Cannot remove namespace after shutdown began", ex); }
        SkillGenerationManager.dispatch(retirement[0]);
    }

    private SkillGenerationManager.PreparationView readWhileOpen(String namespace) {
        final SkillGenerationManager.PreparationView[] result = new SkillGenerationManager.PreparationView[1];
        try { lifecycle.whileAdmissionOpen(() -> result[0] = generations.capturePreparation(namespace)); }
        catch (RejectedExecutionException ex) { throw new SkillReloadException("Cannot prepare skill update after shutdown began", ex); }
        return result[0];
    }

    private void checkOpen()
    {
        try { lifecycle.whileAdmissionOpen(() -> { }); }
        catch (RejectedExecutionException ex)
        { throw new SkillReloadException("Shutdown began during skill preparation", ex); }
    }

    private static void closeRejected(Candidate candidate, RuntimeException primary)
    {
        try { candidate.close(); }
        catch (RuntimeException closeFailure) { primary.addSuppressed(closeFailure); }
    }

    private static final class Candidate implements PreparedSkillUpdate
    {
        private final Object owner;
        private final String baseId;
        private final SkillGeneration generation;
        private boolean published;
        private boolean closed;

        private Candidate(Object owner, String baseId, SkillGeneration generation)
        {
            this.owner = owner;
            this.baseId = baseId;
            this.generation = generation;
        }

        @Override public String generationId() { return generation.id(); }
        @Override public SkillCatalog snapshot() { return generation.skillCatalog(); }
        @Override public void close() {
            synchronized (this) {
                if (closed || published) return;
                closed = true;
            }
            generation.close();
        }
    }
}
