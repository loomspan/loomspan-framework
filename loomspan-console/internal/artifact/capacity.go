package artifact

import (
	"math"
	"sort"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/consolecore"
)

const limitNameMaxBytes = "trace-workspace.max-bytes"

// capacityConfig holds the resolved aggregate capacity policy. It is consumed
// from profile.Resolved without changing config syntax or defaults.
type capacityConfig struct {
	maxBytes  int64
	unlimited bool
}

// reserveCapacity attempts to charge needed bytes for a new or growing
// acquisition. In finite mode it first removes expired unpinned entries, then
// LRU unpinned installed entries, before rejecting with LIMIT_EXCEEDED. In
// unlimited mode it skips policy rejection but still rejects int64 accounting
// overflow; disk-full is handled separately as LOCAL_STORAGE_UNAVAILABLE.
//
// The caller must hold the service mutex.
func (service *Service) reserveCapacity(needed int64) *consolecore.Error {
	if needed < 0 {
		return consolecore.NewError(consolecore.CodeInvalidArtifact,
			"The artifact size is invalid.", "", consolecore.Details{}, nil)
	}
	if service.totalCharged < 0 || needed > math.MaxInt64-service.totalCharged {
		return consolecore.NewError(consolecore.CodeInvalidArtifact,
			"The aggregate artifact size exceeds the supported range.",
			"", consolecore.Details{}, nil)
	}
	if service.capacity.unlimited {
		return nil
	}
	fits := func() bool {
		return service.totalCharged >= 0 &&
			service.totalCharged <= service.capacity.maxBytes &&
			needed <= service.capacity.maxBytes-service.totalCharged
	}
	if fits() {
		return nil
	}
	// Remove expired unpinned entries first.
	if domain := service.removeExpiredUnpinnedLocked(); domain != nil {
		return domain
	}
	if fits() {
		return nil
	}
	// Remove LRU unpinned installed entries until the needed bytes fit.
	for !fits() {
		candidate := service.findLRUUnpinnedInstalledLocked()
		if candidate == nil {
			break
		}
		if domain := service.evictEntryLocked(candidate); domain != nil {
			return domain
		}
	}
	if fits() {
		return nil
	}
	// Even an empty cache cannot fit this artifact.
	if needed > service.capacity.maxBytes {
		return consolecore.NewError(consolecore.CodeLimitExceeded,
			"The trace artifact exceeds the configured trace workspace capacity.",
			"", consolecore.Details{
				LimitName:  limitNameMaxBytes,
				LimitValue: service.capacity.maxBytes,
			}, nil)
	}
	return consolecore.NewError(consolecore.CodeLimitExceeded,
		"The trace workspace is full. Remove unused traces and try again.",
		"", consolecore.Details{
			LimitName:  limitNameMaxBytes,
			LimitValue: service.capacity.maxBytes,
		}, nil)
}

// findLRUUnpinnedInstalledLocked returns the unpinned installed entry with the
// oldest last-successful-use time. Ties are broken deterministically by
// acquisition time then handle so eviction order is stable under -race.
//
// The caller must hold the service mutex.
func (service *Service) findLRUUnpinnedInstalledLocked() *entry {
	var candidates []*entry
	for _, entry := range service.entries {
		if entry.state != stateInstalled || entry.pinCount > 0 {
			continue
		}
		candidates = append(candidates, entry)
	}
	if len(candidates) == 0 {
		return nil
	}
	sort.Slice(candidates, func(i, j int) bool {
		a, b := candidates[i], candidates[j]
		if !a.lastUsedAt.Equal(b.lastUsedAt) {
			return a.lastUsedAt.Before(b.lastUsedAt)
		}
		if !a.acquisitionTime.Equal(b.acquisitionTime) {
			return a.acquisitionTime.Before(b.acquisitionTime)
		}
		return a.handle < b.handle
	})
	return candidates[0]
}

// evictEntryLocked removes an unpinned installed entry, releasing its bytes
// and deleting its file. The caller must hold the service mutex and must have
// verified the entry is not pinned or acquiring.
func (service *Service) evictEntryLocked(entry *entry) *consolecore.Error {
	return service.removeEntryLocked(entry)
}
