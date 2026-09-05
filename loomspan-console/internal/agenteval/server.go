package agenteval

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/applicationclient"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/artifact"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/consolecore"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/live"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/mcpadapter"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/mcpcredential"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/observability"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/profile"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/target"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/traceanalysis"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/traceinventory"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/traceresolution"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/workspace"
)

type RunningServer struct {
	Endpoint  string
	Key       string
	http      *http.Server
	listener  net.Listener
	mcp       *mcpadapter.Server
	artifacts *artifact.Service
	workspace *workspace.Workspace
	profile   *profile.Profile
	target    *target.Context
	live      *live.Service
	upstream  *httptest.Server
}

func StartServer(output string, value Case, version string) (*RunningServer, error) {
	root, err := filepath.Abs(output)
	if err != nil {
		return nil, err
	}
	if err := os.MkdirAll(root, 0o700); err != nil {
		return nil, err
	}
	_ = os.Chmod(root, 0o700)
	owned, err := profile.Open(filepath.Join(root, "profile", "config.yaml"))
	if err != nil {
		return nil, err
	}
	fail := func(e error) (*RunningServer, error) { _ = owned.Close(); return nil, e }
	credentials, err := mcpcredential.Open(owned.Directory, nil)
	if err != nil {
		return fail(err)
	}
	prepared, err := credentials.Prepare()
	if err != nil {
		return fail(err)
	}
	key, err := credentials.CommitEnable(prepared)
	if err != nil {
		return fail(err)
	}
	space, err := workspace.Open(filepath.Join(root, "workspace"))
	if err != nil {
		return fail(err)
	}
	analysis := traceanalysis.NewServiceForCompatibilityVersion(nil, version)
	artifacts, err := artifact.New(artifact.Config{MaxBytes: 256 << 20, IdleTTL: time.Hour}, artifact.Dependencies{Workspace: space, TraceLoader: func(context.Context, target.Scope, string) (artifact.TraceMetadata, *consolecore.Error) {
		return artifact.TraceMetadata{}, consolecore.NewError(consolecore.CodeNotFound, "Unavailable", "", consolecore.Details{}, nil)
	}, StreamOpener: func(context.Context, target.Scope, string) (*applicationclient.ArtifactStream, *consolecore.Error) {
		return nil, consolecore.NewError(consolecore.CodeTargetUnavailable, "Unavailable", "", consolecore.Details{}, nil)
	}, Processor: analysis, Clock: func() time.Time { return time.Date(2026, 9, 1, 12, 0, 0, 0, time.UTC) }})
	if err != nil {
		_ = space.Close()
		return fail(err)
	}
	var targetContext *target.Context
	var liveService *live.Service
	var observabilityService *observability.Service
	var upstream *httptest.Server
	cleanup := func(e error) (*RunningServer, error) {
		if liveService != nil {
			liveService.Close()
		}
		if targetContext != nil {
			targetContext.Close()
		}
		if upstream != nil {
			upstream.Close()
		}
		artifacts.Close()
		_ = space.Close()
		_ = owned.Close()
		return nil, e
	}
	analysis.SetArtifactService(artifacts)
	repo, _ := RepositoryRoot()
	for _, fixture := range value.Fixtures {
		if filepath.Ext(fixture.Path) != ".ndjson" {
			continue
		}
		raw, readErr := os.ReadFile(filepath.Join(repo, filepath.FromSlash(fixture.Path)))
		if readErr != nil {
			return cleanup(readErr)
		}
		if _, domain := artifacts.Import(context.Background(), bytes.NewReader(raw), int64(len(raw))); domain != nil {
			return cleanup(fmt.Errorf("import fixture: %v", domain))
		}
	}
	if value.PairID == "live-concurrency" {
		targetContext, liveService, observabilityService, upstream, err = startLiveFixtureTarget(repo, value, version)
		if err != nil {
			return cleanup(err)
		}
	} else {
		targetContext, err = target.New(func(applicationclient.Address) (target.ProbeClient, error) {
			return nil, fmt.Errorf("evaluation target is intentionally unavailable")
		}, func() (target.ScopeID, error) { return target.ScopeID("eval-unused-scope"), nil }, time.Now)
		if err != nil {
			return cleanup(err)
		}
		targetContext.StartServing()
	}
	inventory := traceinventory.New(artifacts, observabilityService, targetContext, time.Now)
	resolver := traceresolution.New(artifacts, observabilityService, targetContext)
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return cleanup(err)
	}
	_, portText, _ := net.SplitHostPort(listener.Addr().String())
	port, _ := strconv.Atoi(portText)
	status := func() consolecore.StatusSnapshot { return consolecore.NoTargetStatus(time.Now().UTC()) }
	if targetContext != nil {
		status = func() consolecore.StatusSnapshot { return targetContext.Snapshot().Status }
	}
	mcp := mcpadapter.NewServer(mcpadapter.ServerOptions{Port: port, Credentials: credentials, Tracker: mcpadapter.NewTracker(), Status: status, Target: targetContext, Live: liveService, Observability: observabilityService, TraceResolver: resolver, TraceAnalysis: analysis, TraceInventory: inventory})
	httpServer := &http.Server{Handler: mcp.Handler(), ReadHeaderTimeout: 5 * time.Second}
	running := &RunningServer{Endpoint: "http://" + listener.Addr().String() + "/mcp", Key: key, http: httpServer, listener: listener, mcp: mcp, artifacts: artifacts, workspace: space, profile: owned, target: targetContext, live: liveService, upstream: upstream}
	go func() { _ = httpServer.Serve(listener) }()
	return running, nil
}

func startLiveFixtureTarget(repo string, value Case, version string) (*target.Context, *live.Service, *observability.Service, *httptest.Server, error) {
	details := map[string][]byte{}
	var listItems [][]byte
	for _, fixture := range value.Fixtures {
		if strings.Contains(filepath.ToSlash(fixture.Path), "/active-executions/") {
			raw, err := os.ReadFile(filepath.Join(repo, filepath.FromSlash(fixture.Path)))
			if err != nil {
				return nil, nil, nil, nil, err
			}
			var item map[string]any
			if err := json.Unmarshal(raw, &item); err != nil {
				return nil, nil, nil, nil, err
			}
			stem := strings.TrimSuffix(filepath.Base(fixture.Path), filepath.Ext(fixture.Path))
			sessionID := "session-live-" + stem
			item["sessionId"] = sessionID
			item["traceId"] = "trace-live-" + stem
			delete(item, "targetScopeId")
			encoded, err := json.Marshal(item)
			if err != nil {
				return nil, nil, nil, nil, err
			}
			details[sessionID] = encoded
			listItems = append(listItems, encoded)
		}
	}
	if len(listItems) == 0 {
		return nil, nil, nil, nil, fmt.Errorf("live case has no active-execution fixture")
	}
	instance, err := os.ReadFile(filepath.Join(repo, "loomspan-console-fixtures", "application-rest", "instance-status.json"))
	if err != nil {
		return nil, nil, nil, nil, err
	}
	page := append([]byte(`{"items":[`), bytes.Join(listItems, []byte(","))...)
	page = append(page, []byte(`],"hasMore":false,"nextCursor":null,"observedAt":"2026-07-25T12:00:00Z","resumeCursor":"0"}`)...)
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set(applicationclient.InstanceIDHeader, "11111111-1111-4111-8111-111111111111")
		switch {
		case strings.HasSuffix(r.URL.Path, "/instance"):
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write(instance)
		case strings.HasSuffix(r.URL.Path, "/active-executions"):
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write(page)
		case strings.HasSuffix(r.URL.Path, "/activity"):
			w.Header().Set("Content-Type", "text/event-stream")
			if f, ok := w.(http.Flusher); ok {
				f.Flush()
			}
			<-r.Context().Done()
		case strings.Contains(r.URL.Path, "/active-executions/"):
			sessionID := r.URL.Path[strings.LastIndex(r.URL.Path, "/")+1:]
			item, ok := details[sessionID]
			if !ok {
				http.NotFound(w, r)
				return
			}
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write(item)
		default:
			http.NotFound(w, r)
		}
	}))
	factory := func(address applicationclient.Address) (target.ProbeClient, error) {
		return applicationclient.New(address, applicationclient.NetworkPolicy{ConnectTimeout: time.Second, ResponseHeaderTimeout: time.Second, RequestTimeout: 2 * time.Second}, version)
	}
	targetContext, err := target.New(factory, func() (target.ScopeID, error) { return target.ScopeID("eval-live-scope"), nil }, time.Now)
	if err != nil {
		upstream.Close()
		return nil, nil, nil, nil, err
	}
	liveService := live.NewService(context.Background())
	observabilityService := observability.New()
	liveService.SetBaselineLoader(func(ctx context.Context, scope target.Scope) (live.Baseline, *consolecore.Error) {
		page, domain := observabilityService.ListActiveExecutions(ctx, scope, observability.ListRequest{})
		resume := ""
		if page.ResumeCursor != nil {
			resume = *page.ResumeCursor
		}
		return live.Baseline{Executions: page.Items, ResumeCursor: resume, ObservedAt: page.ObservedAt}, domain
	})
	if err := targetContext.RegisterOwner("agent-eval-live", liveService); err != nil {
		liveService.Close()
		targetContext.Close()
		upstream.Close()
		return nil, nil, nil, nil, err
	}
	targetContext.StartServing()
	if _, domain := targetContext.SelectAndConnect(context.Background(), upstream.URL, []byte(strings.Repeat("e", 32))); domain != nil {
		liveService.Close()
		targetContext.Close()
		upstream.Close()
		return nil, nil, nil, nil, fmt.Errorf("connect live fixture: %v", domain)
	}
	return targetContext, liveService, observabilityService, upstream, nil
}
func (s *RunningServer) Close(ctx context.Context) error {
	if s == nil {
		return nil
	}
	var err error
	if s.http != nil {
		err = s.http.Shutdown(ctx)
	}
	if s.mcp != nil {
		s.mcp.CloseSessions()
	}
	if s.live != nil {
		s.live.Close()
	}
	if s.target != nil {
		s.target.Close()
	}
	if s.upstream != nil {
		s.upstream.Close()
	}
	if s.artifacts != nil {
		s.artifacts.Close()
	}
	if s.workspace != nil {
		_ = s.workspace.Close()
	}
	if s.profile != nil {
		_ = s.profile.Close()
	}
	return err
}
