package artifact

import (
	"context"
	"errors"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/consolecore"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/diagnostics"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/evidence"
	"io/fs"
	"syscall"
)

func storageCause(err error, stage string) error {
	cause := "storage_" + stage
	if stage == "install" {
		cause = "storage_write"
	}
	if errors.Is(err, syscall.ENOSPC) {
		cause = "no_space"
	} else if errors.Is(err, fs.ErrPermission) {
		cause = "permission"
	}
	return diagnostics.Annotate(err, diagnostics.Facts{Cause: cause, Stage: stage})
}
func (service *Service) entryDiagnostic(entry *entry, operation string) context.Context {
	source := entry.acquireCtx
	if source == nil {
		source = service.lifetime
	}
	ctx := diagnostics.Detach(context.Background(), source, operation)
	scope := ""
	if entry.key.owner.Source() == evidence.SourceTarget {
		scope = entry.key.owner.ID()
	}
	return diagnostics.WithScope(ctx, scope)
}
func (service *Service) reportAcquisition(entry *entry, domain *consolecore.Error) {
	expected := entry.acquireCtx.Err() != nil || (entry.key.owner.Source() == evidence.SourceImported && (domain.Code == consolecore.CodeInvalidArtifact || domain.Code == consolecore.CodeIncompatibleArtifact))
	facts := diagnostics.Facts{Expected: expected, Cause: artifactCause(domain)}
	if entry.key.owner.Source() == evidence.SourceTarget && (domain.Code == consolecore.CodeInvalidArtifact || domain.Code == consolecore.CodeIncompatibleArtifact) {
		facts.Endpoint = "artifact.download"
	}
	diagnostics.Report(entry.acquireCtx, diagnostics.Annotate(domain, facts))
}
func (service *Service) reportCleanup(entry *entry, err error, stage string) error {
	if err == nil {
		return nil
	}
	err = storageCause(err, stage)
	diagnostics.Report(service.entryDiagnostic(entry, "artifact.cleanup"), err)
	return err
}

func artifactCause(domain *consolecore.Error) string {
	if domain.Code == consolecore.CodeInvalidArtifact {
		return "invalid_content"
	}
	return ""
}

func (service *Service) closeInput(entry *entry, stream inputStream) {
	if err := stream.Close(); err != nil {
		f := diagnostics.Facts{Cause: "body_read", Stage: "close", Expected: entry.acquireCtx.Err() != nil}
		if entry.key.owner.Source() == evidence.SourceTarget {
			f.Endpoint = "artifact.download"
		}
		diagnostics.Report(service.entryDiagnostic(entry, "artifact.cleanup"), diagnostics.Annotate(err, f))
	}
}
