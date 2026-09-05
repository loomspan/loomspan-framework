package traceanalysis

import (
	"context"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/artifact"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/consolecore"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/evidence"
)

// PlanQuery selects complete immutable plan projections in canonical creation order.
type PlanQuery struct {
	Handle   artifact.Handle
	PlanID   string                 `json:"planId,omitempty"`
	PageSize int                    `json:"pageSize"`
	Cursor   string                 `json:"cursor,omitempty"`
	Admit    func(PlanSummary) bool `json:"-"`
}

type planQueryCanonical struct {
	PlanID   string `json:"planId,omitempty"`
	PageSize int    `json:"pageSize"`
}

func (service *Service) QueryPlans(ctx context.Context, scopeID evidence.Reference, query PlanQuery) (Page[PlanSummary], *consolecore.Error) {
	pageSize, domain := validatePageSize(scopeID, query.PageSize)
	if domain != nil {
		return Page[PlanSummary]{}, domain
	}
	fingerprint, err := canonicalizeRequest(planQueryCanonical{PlanID: query.PlanID, PageSize: pageSize})
	if err != nil {
		return Page[PlanSummary]{}, consolecore.NewError(consolecore.CodeConsoleError, "The plan query could not be canonicalized.", scopeID.ID(), consolecore.Details{}, err)
	}
	lease, domain := service.leaseForHandle(scopeID, query.Handle)
	if domain != nil {
		return Page[PlanSummary]{}, domain
	}
	success := false
	defer func() { _ = lease.Close(success) }()
	start := 0
	var decoded cursor
	if query.Cursor != "" {
		var cursorDomain *consolecore.Error
		decoded, start, cursorDomain = prepareCursor(query.Cursor, ownerCursorKey(lease.Owner()), scopeID.ID(), cursorOpPlans)
		if cursorDomain != nil {
			return Page[PlanSummary]{}, cursorDomain
		}
	}
	if decoded.Schema != "" {
		if d := validateCursorFingerprint(decoded, fingerprint, ownerCursorKey(lease.Owner()), scopeID.ID(), query.Handle); d != nil {
			return Page[PlanSummary]{}, d
		}
	}
	if err := ctx.Err(); err != nil {
		return Page[PlanSummary]{}, canceledError(err)
	}
	traceCtx, err := traceContextForLease(lease, scopeID, query.Handle)
	if err != nil {
		return Page[PlanSummary]{}, storageError(scopeID.ID(), err)
	}
	items := make([]PlanSummary, 0, pageSize)
	currentPosition, nextPosition := int64(start), int64(start)
	hasMore := false
	var stopped *consolecore.Error
	err = scanFactRowsContext[PlanSummary](ctx, lease, ComponentPlanIndex, int64(start), func(plan PlanSummary, next int64) bool {
		if err := ctx.Err(); err != nil {
			stopped = canceledError(err)
			return true
		}
		if query.PlanID != "" && plan.PlanID != query.PlanID {
			currentPosition = next
			nextPosition = next
			return false
		}
		if len(items) == pageSize {
			hasMore = true
			return true
		}
		plan.Context = traceCtx
		if query.Admit != nil && !query.Admit(plan) {
			if len(items) == 0 {
				stopped = consolecore.NewError(consolecore.CodeLimitExceeded, "One plan exceeds the safe response budget. Narrow the plan selector or increase the response budget.", scopeID.ID(), consolecore.Details{}, nil)
				return true
			}
			hasMore, nextPosition = true, currentPosition
			return true
		}
		items = append(items, plan)
		currentPosition, nextPosition = next, next
		return false
	})
	if stopped != nil {
		return Page[PlanSummary]{}, stopped
	}
	if err != nil {
		return Page[PlanSummary]{}, storageError(scopeID.ID(), err)
	}
	var nextCursor string
	if hasMore {
		nextCursor, err = encodePositionCursor(cursorOpPlans, ownerCursorKey(lease.Owner()), query.Handle, fingerprint, nextPosition)
		if err != nil {
			return Page[PlanSummary]{}, cursorError(scopeID.ID(), err)
		}
	}
	success = true
	return Page[PlanSummary]{Context: traceCtx, Items: items, NextCursor: nextCursor, HasMore: hasMore}, nil
}
