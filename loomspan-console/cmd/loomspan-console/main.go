package main

import (
	"context"
	"flag"
	"fmt"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/diagnostics"
	"io"
	"log/slog"
	"os"
	"os/signal"
	"syscall"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/applicationclient"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/browserapi"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/browseropen"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/config"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/console"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/credentialprompt"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/release"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/webassets"
)

type runtimeDependencies struct {
	version   string
	verify    func() error
	serve     func(context.Context, console.Options) error
	lookupEnv func(string) string
}

func main() {
	slog.SetDefault(diagnostics.NewLogger(os.Stderr))
	startup := diagnostics.Operation(context.Background(), "console.startup")
	files, err := webassets.Embedded()
	if err != nil {
		diagnostics.Report(startup, diagnostics.Annotate(err, diagnostics.Facts{Cause: "assets", Stage: "startup"}))
		os.Exit(1)
	}
	dependencies := runtimeDependencies{
		version: release.ProductVersion(),
		verify: func() error {
			_, err := webassets.Verify(files, release.ProductVersion())
			return err
		},
		serve: func(ctx context.Context, options console.Options) error {
			return console.Run(ctx, options, console.Dependencies{
				Files:       files,
				Output:      os.Stdout,
				OpenBrowser: browseropen.Open,
				PromptApplicationKey: func(context.Context) ([]byte, error) {
					return credentialprompt.Read(os.Stdin, os.Stderr)
				},
			})
		},
		lookupEnv: os.Getenv,
	}
	context, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	if err := run(context, os.Args[1:], os.Stdout, dependencies); err != nil {
		diagnostics.Report(startup, err)
		os.Exit(1)
	}
}

func run(context context.Context, arguments []string, output io.Writer, dependencies runtimeDependencies) (result error) {
	stage := "configuration"
	defer func() {
		if result != nil {
			result = diagnostics.Annotate(result, diagnostics.Facts{Cause: stage, Stage: "startup"})
		}
	}()
	flags := flag.NewFlagSet("loomspan-console", flag.ContinueOnError)
	flags.SetOutput(io.Discard)
	versionOnly := flags.Bool("version", false, "print the Loomspan product version")
	configPath := flags.String("config", "", "exact Console configuration file")
	workDirectory := flags.String("work-dir", "", "exact managed Console work directory")
	address := flags.String("listen", "", "process-only explicit loopback listener override")
	targetAddress := flags.String("target-address", "", "prefill the browser target address without connecting")
	developmentOrigin := flags.String("development-origin", "", "additional exact loopback Vite origin")
	noOpenBrowser := flags.Bool("no-open-browser", false, "do not open the default browser")
	promptApplicationKey := flags.Bool("prompt-for-application-key", false, "prompt without echo for the selected target application key")
	if err := flags.Parse(arguments); err != nil {
		return err
	}
	if flags.NArg() != 0 {
		return fmt.Errorf("unexpected arguments: %v", flags.Args())
	}
	if err := release.ValidateProductVersion(dependencies.version); err != nil {
		return err
	}
	if dependencies.verify == nil || dependencies.serve == nil {
		return fmt.Errorf("runtime dependencies are incomplete")
	}
	stage = "assets"
	if err := dependencies.verify(); err != nil {
		return fmt.Errorf("validate embedded browser assets: %w", err)
	}
	stage = "configuration"
	if *versionOnly {
		_, err := fmt.Fprintln(output, dependencies.version)
		return err
	}
	if *address != "" {
		if err := config.ValidateListenerAddress(*address); err != nil {
			return fmt.Errorf("--listen: %w", err)
		}
	}
	if *developmentOrigin != "" {
		if _, _, err := browserapi.ParseLoopbackOrigin(*developmentOrigin); err != nil {
			return fmt.Errorf("--development-origin: %w", err)
		}
	}
	if *targetAddress != "" {
		if _, err := applicationclient.NormalizeAddress(*targetAddress); err != nil {
			return fmt.Errorf("--target-address: %w", err)
		}
	}
	applicationKey := ""
	if dependencies.lookupEnv != nil {
		applicationKey = dependencies.lookupEnv("LOOMSPAN_OBSERVABILITY_API_KEY")
	}
	if applicationKey != "" {
		if err := applicationclient.ValidateCredential([]byte(applicationKey)); err != nil {
			return fmt.Errorf("LOOMSPAN_OBSERVABILITY_API_KEY: %w", err)
		}
	}
	stage = "unknown"
	return dependencies.serve(diagnostics.Operation(context, "console.run"), console.Options{
		ConfigPath:              *configPath,
		WorkDirectory:           *workDirectory,
		ListenOverride:          *address,
		DevelopmentOrigin:       *developmentOrigin,
		NoOpenBrowser:           *noOpenBrowser,
		PromptForApplicationKey: *promptApplicationKey,
		TargetAddressDefault:    *targetAddress,
		ApplicationKeyDefault:   applicationKey,
	})
}
