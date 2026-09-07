package applicationclient

import (
	"fmt"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/diagnostics"
	"sync/atomic"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/consolecore"
)

type FailureKind string

const (
	FailureAuthentication            FailureKind = "authentication"
	FailureAccess                    FailureKind = "access"
	FailureIncompatible              FailureKind = "incompatible"
	FailureUnavailable               FailureKind = "unavailable"
	FailureProtocol                  FailureKind = "protocol"
	FailureInvalidArgument           FailureKind = "invalid_argument"
	FailureInvalidCursor             FailureKind = "invalid_cursor"
	FailureStaleCursor               FailureKind = "stale_cursor"
	FailureNotFound                  FailureKind = "not_found"
	FailureLimitExceeded             FailureKind = "limit_exceeded"
	FailureLiveMonitoringUnavailable FailureKind = "live_monitoring_unavailable"
)

type TransportCategory string

const (
	CategoryDNS                TransportCategory = "dns"
	CategoryConnection         TransportCategory = "connection"
	CategoryTimeout            TransportCategory = "timeout"
	CategoryTLSUntrustedIssuer TransportCategory = "tls_untrusted_issuer"
	CategoryTLSHostname        TransportCategory = "tls_hostname_mismatch"
	CategoryTLSExpired         TransportCategory = "tls_expired"
	CategoryTLSNotYetValid     TransportCategory = "tls_not_yet_valid"
	CategoryTLSHandshake       TransportCategory = "tls_handshake"
	CategoryRedirect           TransportCategory = "redirect"
	CategoryNamespaceNotFound  TransportCategory = "namespace_not_found"
	CategoryUpstreamServer     TransportCategory = "upstream_server"
	CategoryUpstreamProtocol   TransportCategory = "upstream_protocol"
)

type Failure struct {
	Kind      FailureKind
	Category  TransportCategory
	Expected  string
	Observed  string
	Retryable bool
	cause     error
	claim     *atomic.Bool
}

func (failure *Failure) Error() string {
	switch failure.Kind {
	case FailureAuthentication:
		return "Application authentication is required."
	case FailureAccess:
		return "The selected target denied access."
	case FailureIncompatible:
		return "The selected target is not compatible with this Console release."
	case FailureInvalidArgument:
		return "The request was invalid."
	case FailureInvalidCursor:
		return "The continuation is invalid."
	case FailureStaleCursor:
		return "The continuation belongs to another application instance."
	case FailureNotFound:
		return "The requested observability resource was not found."
	case FailureLimitExceeded:
		return "The observability response exceeds the configured limit."
	case FailureLiveMonitoringUnavailable:
		return "Live execution monitoring is unavailable."
	default:
		return "The selected target is unavailable."
	}
}

func (failure *Failure) Unwrap() error { return failure.cause }

func (failure *Failure) DiagnosticClaim() *atomic.Bool {
	return diagnostics.ClaimOr(failure.cause, diagnostics.EnsureClaim(&failure.claim))
}
func (failure *Failure) DiagnosticFacts() diagnostics.Facts {
	f := diagnostics.Facts{Classification: string(failure.Kind), Cause: string(failure.Category)}
	switch failure.Kind {
	case FailureAuthentication, FailureAccess, FailureInvalidArgument, FailureInvalidCursor, FailureStaleCursor, FailureNotFound, FailureLiveMonitoringUnavailable:
		f.Expected = true
	}
	return f
}

func (failure *Failure) ConsoleError(scopeID string) *consolecore.Error {
	details := consolecore.Details{TransportCategory: string(failure.Category)}
	switch failure.Kind {
	case FailureAuthentication:
		return consolecore.NewError(consolecore.CodeTargetAuthentication, "The application key was rejected.", scopeID, details, failure)
	case FailureAccess:
		return consolecore.NewError(consolecore.CodeTargetAccessBlocked, "The selected target denied access before Loomspan authentication.", scopeID, details, failure)
	case FailureIncompatible:
		details.ExpectedCompatibilityVersion = failure.Expected
		details.ObservedCompatibilityVersion = failure.Observed
		return consolecore.NewError(consolecore.CodeIncompatibleTarget, "The selected target uses a different Loomspan release.", scopeID, details, failure)
	case FailureInvalidArgument:
		return consolecore.NewError(consolecore.CodeInvalidArgument, "The request was invalid.", scopeID, details, failure)
	case FailureInvalidCursor:
		return consolecore.NewError(consolecore.CodeInvalidCursor, "The continuation is invalid.", scopeID, details, failure)
	case FailureStaleCursor:
		return consolecore.NewError(consolecore.CodeStaleCursor, "The continuation belongs to another application instance.", scopeID, details, failure)
	case FailureNotFound:
		return consolecore.NewError(consolecore.CodeNotFound, "The requested observability resource was not found.", scopeID, details, failure)
	case FailureLimitExceeded:
		return consolecore.NewError(consolecore.CodeLimitExceeded, "The observability response exceeds the configured limit.", scopeID, details, failure)
	case FailureLiveMonitoringUnavailable:
		return consolecore.NewError(consolecore.CodeLiveMonitoringUnavailable, "Live execution monitoring is unavailable.", scopeID, details, failure)
	default:
		return consolecore.NewError(consolecore.CodeTargetUnavailable, "The selected target is unavailable.", scopeID, details, failure)
	}
}

func newFailure(kind FailureKind, category TransportCategory, cause error) *Failure {
	return &Failure{Kind: kind, Category: category, cause: cause, claim: diagnostics.Claim(cause)}
}

func protocolFailure() *Failure {
	return newFailure(FailureProtocol, CategoryUpstreamProtocol, diagnostics.Annotate(fmt.Errorf("invalid upstream protocol"), diagnostics.Facts{Cause: "response_decode"}))
}

func protocolCause(err error) *Failure {
	return newFailure(FailureProtocol, CategoryUpstreamProtocol, err)
}
func protocolReason(reason string) *Failure {
	return protocolCause(diagnostics.Annotate(fmt.Errorf("invalid upstream protocol"), diagnostics.Facts{Cause: reason}))
}

func annotateFailure(err error, facts diagnostics.Facts) error {
	if failure, ok := err.(*Failure); ok {
		failure.cause = diagnostics.Annotate(failure.cause, facts)
		return failure
	}
	return diagnostics.Annotate(err, facts)
}
