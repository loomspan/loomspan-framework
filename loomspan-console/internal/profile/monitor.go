package profile

import (
	"context"
	"fmt"
	"time"
)

func (profile *Profile) Monitor(context context.Context, interval time.Duration, fatal func(error)) {
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
			if err := profile.Check(); err != nil {
				fatal(fmt.Errorf("profile invariant lost: %w", err))
				return
			}
		}
	}
}
