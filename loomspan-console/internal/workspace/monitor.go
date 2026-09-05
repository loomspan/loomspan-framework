package workspace

import (
	"context"
	"fmt"
	"time"
)

func (workspace *Workspace) Monitor(context context.Context, interval time.Duration, fatal func(error)) {
	if interval <= 0 {
		interval = 30 * time.Second
	}
	ticker := time.NewTicker(interval)
	defer ticker.Stop()
	for {
		select {
		case <-context.Done():
			return
		case <-ticker.C:
			if err := workspace.Check(); err != nil {
				fatal(fmt.Errorf("workspace invariant lost: %w", err))
				return
			}
		}
	}
}
