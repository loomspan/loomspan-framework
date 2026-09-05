package config

import "time"

const (
	SchemaVersion                = 1
	DefaultAddress               = "127.0.0.1:7943"
	DefaultMaxBytes              = int64(4 * 1024 * 1024 * 1024)
	DefaultIdleTTL               = 4 * time.Hour
	DefaultConnectTimeout        = 5 * time.Second
	DefaultResponseHeaderTimeout = 10 * time.Second
	DefaultRequestTimeout        = 30 * time.Second
)

type File struct {
	Version        int            `yaml:"version"`
	Listener       Listener       `yaml:"listener"`
	TraceWorkspace TraceWorkspace `yaml:"trace-workspace"`
	Target         *Target        `yaml:"target,omitempty"`
}

type Listener struct {
	Address string `yaml:"address"`
}

type TraceWorkspace struct {
	MaxBytes string `yaml:"max-bytes"`
	IdleTTL  string `yaml:"idle-ttl"`
}

type Target struct {
	Address               string `yaml:"address"`
	ConnectTimeout        string `yaml:"connect-timeout,omitempty"`
	ResponseHeaderTimeout string `yaml:"response-header-timeout,omitempty"`
	RequestTimeout        string `yaml:"request-timeout,omitempty"`
	CABundle              string `yaml:"ca-bundle,omitempty"`
}

type Resolved struct {
	ListenerAddress string
	MaxBytes        int64
	Unlimited       bool
	IdleTTL         time.Duration
	NeverExpire     bool
	Target          *ResolvedTarget
}

type ResolvedTarget struct {
	Address               string
	ConnectTimeout        time.Duration
	ResponseHeaderTimeout time.Duration
	RequestTimeout        time.Duration
	CABundlePath          string
	CABundlePEM           []byte
}

func Default() File {
	return File{
		Version:  SchemaVersion,
		Listener: Listener{Address: DefaultAddress},
		TraceWorkspace: TraceWorkspace{
			MaxBytes: "4GiB",
			IdleTTL:  "4h",
		},
	}
}

const DefaultYAML = `version: 1
listener:
  address: 127.0.0.1:7943
trace-workspace:
  max-bytes: 4GiB
  idle-ttl: 4h
`
