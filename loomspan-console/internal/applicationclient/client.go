package applicationclient

import (
	"context"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net"
	"net/http"
	"strings"
	"time"
)

const (
	APIKeyHeader     = "X-loomspan-Api-Key"
	InstanceIDHeader = "X-loomspan-Instance-Id"
	maxResponseBytes = 64 * 1024
	problemMaxBytes  = 4 * 1024
)

type Credential interface {
	Apply(*http.Request) error
}

type NetworkPolicy struct {
	ConnectTimeout        time.Duration
	ResponseHeaderTimeout time.Duration
	RequestTimeout        time.Duration
	CABundlePEM           []byte
}

type Client struct {
	address         Address
	expectedVersion string
	requestTimeout  time.Duration
	http            *http.Client
	transport       *http.Transport
}

type Instance struct {
	InstanceID                  string
	ConsoleCompatibilityVersion string
	ObservedAt                  time.Time
	LiveMonitoringAvailable     bool
}

func New(address Address, policy NetworkPolicy, expectedVersion string) (*Client, error) {
	if address.String() == "" || expectedVersion == "" {
		return nil, fmt.Errorf("application client configuration is incomplete")
	}
	if policy.ConnectTimeout <= 0 || policy.ResponseHeaderTimeout <= 0 || policy.RequestTimeout <= 0 {
		return nil, fmt.Errorf("application client timeouts must be positive")
	}
	roots, err := x509.SystemCertPool()
	if err != nil || roots == nil {
		roots = x509.NewCertPool()
	}
	if len(policy.CABundlePEM) > 0 && !roots.AppendCertsFromPEM(policy.CABundlePEM) {
		return nil, fmt.Errorf("application CA bundle contains no certificates")
	}
	transport := &http.Transport{
		Proxy:                  nil,
		DialContext:            (&net.Dialer{Timeout: policy.ConnectTimeout, KeepAlive: 30 * time.Second}).DialContext,
		ForceAttemptHTTP2:      true,
		MaxIdleConns:           16,
		MaxIdleConnsPerHost:    4,
		IdleConnTimeout:        90 * time.Second,
		TLSHandshakeTimeout:    policy.ConnectTimeout,
		ResponseHeaderTimeout:  policy.ResponseHeaderTimeout,
		ExpectContinueTimeout:  time.Second,
		DisableCompression:     true,
		MaxResponseHeaderBytes: 64 * 1024,
		TLSClientConfig:        &tls.Config{RootCAs: roots, MinVersion: tls.VersionTLS12},
	}
	httpClient := &http.Client{
		Transport:     transport,
		CheckRedirect: func(_ *http.Request, _ []*http.Request) error { return http.ErrUseLastResponse },
	}
	return &Client{
		address: address, expectedVersion: expectedVersion,
		requestTimeout: policy.RequestTimeout, http: httpClient, transport: transport,
	}, nil
}

func (client *Client) Close() { client.transport.CloseIdleConnections() }

func (client *Client) Get(parent context.Context, endpoint string, maxBytes int64, credential Credential) ([]byte, string, error) {
	if credential == nil {
		return nil, "", newFailure(FailureAuthentication, "", nil)
	}
	requestContext, cancel := context.WithTimeout(parent, client.requestTimeout)
	defer cancel()
	request, err := http.NewRequestWithContext(requestContext, http.MethodGet, endpoint, nil)
	if err != nil {
		return nil, "", protocolFailure()
	}
	request.Header.Set("Accept", "application/json")
	request.Header.Set("Accept-Encoding", "identity")
	request.Header.Set("Cache-Control", "no-store")
	if err := credential.Apply(request); err != nil {
		return nil, "", newFailure(FailureAuthentication, "", nil)
	}
	if len(request.Header.Values(APIKeyHeader)) != 1 {
		return nil, "", newFailure(FailureAuthentication, "", nil)
	}
	response, err := client.http.Do(request)
	if err != nil {
		if errors.Is(err, context.Canceled) {
			return nil, "", context.Canceled
		}
		return nil, "", classifyTransport(err)
	}
	defer response.Body.Close()
	if response.StatusCode >= 300 && response.StatusCode < 400 {
		return nil, "", newFailure(FailureUnavailable, CategoryRedirect, nil)
	}
	if encoding := response.Header.Get("Content-Encoding"); encoding != "" && !strings.EqualFold(encoding, "identity") {
		return nil, "", protocolFailure()
	}
	if response.StatusCode != http.StatusOK {
		body, err := readBounded(response.Body, problemMaxBytes)
		if err != nil {
			slog.Error("upstream error body read failed", "status", response.StatusCode, "limit", problemMaxBytes, "err", err)
			return nil, "", protocolFailure()
		}
		failure := mapProblem(response.StatusCode, response.Header.Get("Content-Type"), body)
		if f, ok := failure.(*Failure); ok {
			slog.Error("upstream returned non-200", "status", response.StatusCode, "failureKind", f.Kind)
		} else {
			slog.Error("upstream returned non-200", "status", response.StatusCode)
		}
		instanceID, identityErr := optionalResponseInstanceID(response.Header.Values(InstanceIDHeader))
		if identityErr != nil {
			return nil, "", protocolFailure()
		}
		return nil, instanceID, failure
	}
	instanceID, err := responseInstanceID(response.Header.Values(InstanceIDHeader))
	if err != nil {
		return nil, "", protocolFailure()
	}
	body, err := readBounded(response.Body, maxBytes)
	if err != nil {
		slog.Error("upstream response body exceeds limit", "maxBytes", maxBytes, "err", err)
		return nil, "", newFailure(FailureLimitExceeded, "", nil)
	}
	return body, instanceID, nil
}

func (client *Client) Probe(parent context.Context, credential Credential) (Instance, error) {
	if credential == nil {
		return Instance{}, newFailure(FailureAuthentication, "", nil)
	}
	requestContext, cancel := context.WithTimeout(parent, client.requestTimeout)
	defer cancel()
	request, err := http.NewRequestWithContext(requestContext, http.MethodGet, client.address.InstanceEndpoint(), nil)
	if err != nil {
		return Instance{}, protocolFailure()
	}
	request.Header.Set("Accept", "application/json")
	request.Header.Set("Accept-Encoding", "identity")
	request.Header.Set("Cache-Control", "no-store")
	if err := credential.Apply(request); err != nil {
		return Instance{}, newFailure(FailureAuthentication, "", nil)
	}
	if len(request.Header.Values(APIKeyHeader)) != 1 {
		return Instance{}, newFailure(FailureAuthentication, "", nil)
	}
	response, err := client.http.Do(request)
	if err != nil {
		if errors.Is(err, context.Canceled) {
			return Instance{}, context.Canceled
		}
		return Instance{}, classifyTransport(err)
	}
	defer response.Body.Close()
	if response.StatusCode >= 300 && response.StatusCode < 400 {
		return Instance{}, newFailure(FailureUnavailable, CategoryRedirect, nil)
	}
	if encoding := response.Header.Get("Content-Encoding"); encoding != "" && !strings.EqualFold(encoding, "identity") {
		return Instance{}, protocolFailure()
	}
	body, err := readBounded(response.Body, maxResponseBytes)
	if err != nil {
		return Instance{}, protocolFailure()
	}
	if response.StatusCode != http.StatusOK {
		return Instance{}, mapProblem(response.StatusCode, response.Header.Get("Content-Type"), body)
	}
	return client.decodeInstance(response.Header.Values(InstanceIDHeader), body)
}

func (client *Client) decodeInstance(headers []string, body []byte) (Instance, error) {
	var compatibility struct {
		ConsoleCompatibilityVersion json.RawMessage `json:"consoleCompatibilityVersion"`
	}
	if err := decodeOne(body, &compatibility); err != nil {
		return Instance{}, protocolFailure()
	}
	var observedVersion string
	if err := json.Unmarshal(compatibility.ConsoleCompatibilityVersion, &observedVersion); err != nil {
		return Instance{}, protocolFailure()
	}
	if observedVersion != client.expectedVersion {
		return Instance{}, &Failure{
			Kind: FailureIncompatible, Expected: client.expectedVersion, Observed: observedVersion,
		}
	}
	var wire struct {
		InstanceID                  string    `json:"instanceId"`
		ConsoleCompatibilityVersion string    `json:"consoleCompatibilityVersion"`
		ObservedAt                  time.Time `json:"observedAt"`
		LiveMonitoringAvailable     *bool     `json:"liveMonitoringAvailable"`
	}
	if err := decodeOne(body, &wire); err != nil || len(headers) != 1 ||
		wire.InstanceID == "" || headers[0] != wire.InstanceID ||
		wire.ObservedAt.IsZero() || wire.LiveMonitoringAvailable == nil {
		return Instance{}, protocolFailure()
	}
	if _, err := parseUUID(wire.InstanceID); err != nil {
		return Instance{}, protocolFailure()
	}
	return Instance{
		InstanceID: wire.InstanceID, ConsoleCompatibilityVersion: wire.ConsoleCompatibilityVersion,
		ObservedAt: wire.ObservedAt, LiveMonitoringAvailable: *wire.LiveMonitoringAvailable,
	}, nil
}

func ValidateCredential(value []byte) error {
	if len(value) < 32 || len(value) > 512 {
		return fmt.Errorf("application key must contain 32 to 512 printable ASCII characters")
	}
	for _, current := range value {
		if current < 0x21 || current > 0x7e {
			return fmt.Errorf("application key must contain 32 to 512 printable ASCII characters")
		}
	}
	return nil
}

func readBounded(reader io.Reader, maxBytes int64) ([]byte, error) {
	content, err := io.ReadAll(io.LimitReader(reader, maxBytes+1))
	if err != nil || int64(len(content)) > maxBytes {
		return nil, fmt.Errorf("upstream body exceeds limit")
	}
	return content, nil
}

func responseInstanceID(values []string) (string, error) {
	if len(values) != 1 || strings.Contains(values[0], ",") {
		return "", fmt.Errorf("response must contain exactly one instance ID")
	}
	if _, err := parseUUID(values[0]); err != nil {
		return "", err
	}
	return values[0], nil
}

func optionalResponseInstanceID(values []string) (string, error) {
	if len(values) == 0 {
		return "", nil
	}
	return responseInstanceID(values)
}

func decodeOne(content []byte, value any) error {
	decoder := json.NewDecoder(strings.NewReader(string(content)))
	if err := decoder.Decode(value); err != nil {
		return err
	}
	var trailing any
	if err := decoder.Decode(&trailing); err != io.EOF {
		return fmt.Errorf("trailing JSON")
	}
	return nil
}

func classifyTransport(err error) *Failure {
	failure := newFailure(FailureUnavailable, CategoryConnection, err)
	failure.Retryable = true
	var dns *net.DNSError
	if errors.As(err, &dns) {
		failure.Category = CategoryDNS
		return failure
	}
	if errors.Is(err, context.DeadlineExceeded) {
		failure.Category = CategoryTimeout
		return failure
	}
	var hostError x509.HostnameError
	if errors.As(err, &hostError) {
		failure.Category = CategoryTLSHostname
		failure.Retryable = false
		return failure
	}
	var certificateError x509.CertificateInvalidError
	if errors.As(err, &certificateError) {
		switch certificateError.Reason {
		case x509.Expired:
			if certificateError.Cert == nil || certificateError.Cert.NotAfter.Before(time.Now()) {
				failure.Category = CategoryTLSExpired
			} else {
				failure.Category = CategoryTLSNotYetValid
			}
		default:
			failure.Category = CategoryTLSHandshake
		}
		failure.Retryable = false
		return failure
	}
	var unknownAuthority x509.UnknownAuthorityError
	if errors.As(err, &unknownAuthority) {
		failure.Category = CategoryTLSUntrustedIssuer
		failure.Retryable = false
		return failure
	}
	var operation *net.OpError
	if errors.As(err, &operation) && operation.Timeout() {
		failure.Category = CategoryTimeout
	}
	return failure
}

func parseUUID(value string) (string, error) {
	if len(value) != 36 {
		return "", fmt.Errorf("invalid UUID")
	}
	for index, current := range value {
		if index == 8 || index == 13 || index == 18 || index == 23 {
			if current != '-' {
				return "", fmt.Errorf("invalid UUID")
			}
			continue
		}
		if !((current >= '0' && current <= '9') || (current >= 'a' && current <= 'f') || (current >= 'A' && current <= 'F')) {
			return "", fmt.Errorf("invalid UUID")
		}
	}
	return value, nil
}
