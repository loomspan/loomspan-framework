package config

import (
	"bytes"
	"crypto/x509"
	"encoding/pem"
	"fmt"
	"io"
	"math"
	"net"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

func (file File) Resolve() (Resolved, error) {
	return file.resolve("")
}

func (file File) resolve(configPath string) (Resolved, error) {
	if file.Version != SchemaVersion {
		return Resolved{}, fmt.Errorf("version: must be 1")
	}
	if err := ValidateListenerAddress(file.Listener.Address); err != nil {
		return Resolved{}, fmt.Errorf("listener.address: %w", err)
	}
	maxBytes, unlimited, err := parseBytes(file.TraceWorkspace.MaxBytes)
	if err != nil {
		return Resolved{}, fmt.Errorf("trace-workspace.max-bytes: %w", err)
	}
	idleTTL, never, err := parseDuration(file.TraceWorkspace.IdleTTL)
	if err != nil {
		return Resolved{}, fmt.Errorf("trace-workspace.idle-ttl: %w", err)
	}
	resolved := Resolved{
		ListenerAddress: file.Listener.Address,
		MaxBytes:        maxBytes,
		Unlimited:       unlimited,
		IdleTTL:         idleTTL,
		NeverExpire:     never,
	}
	if file.Target != nil {
		target, err := resolveTarget(*file.Target, configPath)
		if err != nil {
			return Resolved{}, err
		}
		resolved.Target = target
	}
	return resolved, nil
}

func resolveTarget(target Target, configPath string) (*ResolvedTarget, error) {
	if target.Address == "" || target.Address != strings.TrimSpace(target.Address) || len(target.Address) > 2048 {
		return nil, fmt.Errorf("target.address: must be nonblank, unpadded, and at most 2048 bytes")
	}
	connect, err := parseNetworkDuration(target.ConnectTimeout, DefaultConnectTimeout)
	if err != nil {
		return nil, fmt.Errorf("target.connect-timeout: %w", err)
	}
	header, err := parseNetworkDuration(target.ResponseHeaderTimeout, DefaultResponseHeaderTimeout)
	if err != nil {
		return nil, fmt.Errorf("target.response-header-timeout: %w", err)
	}
	request, err := parseNetworkDuration(target.RequestTimeout, DefaultRequestTimeout)
	if err != nil {
		return nil, fmt.Errorf("target.request-timeout: %w", err)
	}
	resolved := &ResolvedTarget{
		Address: target.Address, ConnectTimeout: connect,
		ResponseHeaderTimeout: header, RequestTimeout: request,
	}
	if target.CABundle == "" {
		return resolved, nil
	}
	if target.CABundle != strings.TrimSpace(target.CABundle) {
		return nil, fmt.Errorf("target.ca-bundle: must be an unpadded path")
	}
	bundlePath := target.CABundle
	if !filepath.IsAbs(bundlePath) {
		if configPath == "" {
			return nil, fmt.Errorf("target.ca-bundle: relative path requires a configuration path")
		}
		bundlePath = filepath.Join(filepath.Dir(configPath), bundlePath)
	}
	bundlePath = filepath.Clean(bundlePath)
	content, err := readCABundle(bundlePath)
	if err != nil {
		return nil, fmt.Errorf("target.ca-bundle: %w", err)
	}
	resolved.CABundlePath = bundlePath
	resolved.CABundlePEM = content
	return resolved, nil
}

func parseNetworkDuration(value string, fallback time.Duration) (time.Duration, error) {
	if value == "" {
		return fallback, nil
	}
	duration, never, err := parseDuration(value)
	if err != nil || never {
		return 0, fmt.Errorf("must be a positive canonical duration using s, m, or h")
	}
	return duration, nil
}

func readCABundle(path string) ([]byte, error) {
	info, err := os.Stat(path)
	if err != nil || !info.Mode().IsRegular() {
		return nil, fmt.Errorf("must identify a readable regular PEM certificate file")
	}
	const maximum = 1 << 20
	if info.Size() > maximum {
		return nil, fmt.Errorf("must not exceed 1048576 bytes")
	}
	file, err := os.Open(path)
	if err != nil {
		return nil, fmt.Errorf("must identify a readable regular PEM certificate file")
	}
	defer file.Close()
	content, err := io.ReadAll(io.LimitReader(file, maximum+1))
	if err != nil || len(content) > maximum {
		return nil, fmt.Errorf("could not be read within the size limit")
	}
	rest := content
	found := false
	for len(bytes.TrimSpace(rest)) > 0 {
		block, remaining := pem.Decode(rest)
		if block == nil {
			return nil, fmt.Errorf("must contain only valid PEM blocks")
		}
		if block.Type == "CERTIFICATE" {
			if _, err := x509.ParseCertificate(block.Bytes); err != nil {
				return nil, fmt.Errorf("contains an invalid certificate")
			}
			found = true
		}
		rest = remaining
	}
	if !found {
		return nil, fmt.Errorf("must contain at least one certificate")
	}
	return append([]byte(nil), content...), nil
}

func ValidateListenerAddress(address string) error {
	if address == "" || address != strings.TrimSpace(address) || strings.Contains(address, ",") {
		return fmt.Errorf("must be an explicit loopback IP and port")
	}
	host, port, err := net.SplitHostPort(address)
	if err != nil {
		return fmt.Errorf("must be an explicit loopback IP and port")
	}
	ip := net.ParseIP(host)
	if ip == nil || !ip.IsLoopback() || strings.Contains(host, "%") {
		return fmt.Errorf("must use an explicit loopback IP")
	}
	number, err := strconv.ParseUint(port, 10, 16)
	if err != nil {
		return fmt.Errorf("port must be between 0 and 65535")
	}
	if number > math.MaxUint16 {
		return fmt.Errorf("port must be between 0 and 65535")
	}
	return nil
}

func parseBytes(value string) (int64, bool, error) {
	if value == "unlimited" {
		return 0, true, nil
	}
	units := map[string]int64{
		"KiB": 1 << 10,
		"MiB": 1 << 20,
		"GiB": 1 << 30,
		"TiB": 1 << 40,
	}
	for suffix, multiplier := range units {
		if strings.HasSuffix(value, suffix) {
			number := strings.TrimSuffix(value, suffix)
			if number == "" || strings.HasPrefix(number, "+") || strings.ContainsAny(number, ".-") {
				break
			}
			parsed, err := strconv.ParseInt(number, 10, 64)
			if err != nil || parsed <= 0 || parsed > math.MaxInt64/multiplier {
				break
			}
			return parsed * multiplier, false, nil
		}
	}
	return 0, false, fmt.Errorf("must be a positive integer with KiB, MiB, GiB, or TiB, or unlimited")
}

func parseDuration(value string) (time.Duration, bool, error) {
	if value == "never" {
		return 0, true, nil
	}
	if value == "" || strings.HasPrefix(value, "+") || strings.ContainsAny(value, ".-") {
		return 0, false, fmt.Errorf("must be a positive canonical duration or never")
	}
	unit := value[len(value)-1:]
	if unit != "s" && unit != "m" && unit != "h" {
		return 0, false, fmt.Errorf("must use s, m, or h units, or never")
	}
	number := value[:len(value)-1]
	if number == "" {
		return 0, false, fmt.Errorf("must be a positive canonical duration or never")
	}
	parsed, err := strconv.ParseUint(number, 10, 63)
	if err != nil || parsed == 0 {
		return 0, false, fmt.Errorf("must be a positive canonical duration or never")
	}
	duration, err := time.ParseDuration(value)
	if err != nil || duration <= 0 {
		return 0, false, fmt.Errorf("must be a positive canonical duration or never")
	}
	return duration, false, nil
}
