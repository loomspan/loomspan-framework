package browserauth

import (
	"bytes"
	"context"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/diagnostics"
	"strings"
	"testing"
	"time"
)

func TestBrowserLifecycleDiagnosticsExcludeAuthenticationIDs(t *testing.T) {
	var output bytes.Buffer
	now := time.Now()
	registry := NewRegistry(func() time.Time { return now }, nil)
	ctx := diagnostics.WithRequest(diagnostics.WithLogger(context.Background(), diagnostics.NewLogger(&output)), "browser.exchange")
	session, err := registry.CreateSession(ctx)
	if err != nil {
		t.Fatal(err)
	}
	tab, err := registry.Bootstrap(ctx, session, "")
	if err != nil {
		t.Fatal(err)
	}
	for i := 0; i < 100; i++ {
		registry.Authenticate(session)
		_, _ = registry.Bootstrap(ctx, session, tab.TabID)
	}
	registry.ReleaseTab(session, tab.TabID)
	registry.ReleaseTab(session, tab.TabID)
	now = now.Add(SessionIdle)
	registry.Authenticate(session)
	registry.Authenticate(session)
	text := output.String()
	if strings.Count(text, `"request_id":`) != 4 {
		t.Fatal("lifecycle correlation missing: ", text)
	}
	if strings.Count(text, `"state":"created"`) != 2 || strings.Count(text, `"state":"released"`) != 1 || strings.Count(text, `"state":"expired"`) != 1 {
		t.Fatal(text)
	}
	for _, secret := range []string{session, tab.TabID, tab.CSRF} {
		if strings.Contains(text, secret) {
			t.Fatal("authentication value leaked")
		}
	}
}
