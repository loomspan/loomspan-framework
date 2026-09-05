package config

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"math/big"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestDecodeAcceptsCanonicalVersionOneDefaults(t *testing.T) {
	file, resolved, err := Decode("config.yaml", strings.NewReader(DefaultYAML))
	if err != nil {
		t.Fatal(err)
	}
	if file.Version != 1 || resolved.ListenerAddress != DefaultAddress ||
		resolved.MaxBytes != DefaultMaxBytes || resolved.IdleTTL != DefaultIdleTTL {
		t.Fatalf("unexpected resolved config: %#v %#v", file, resolved)
	}
}

func TestDecodeAcceptsOptionalTargetConfigurationAndAppliesNetworkDefaults(t *testing.T) {
	input := DefaultYAML + "target:\n  address: https://Application.Example:443/context/\n"
	file, resolved, err := Decode(`C:\profiles\loomspan-console.yaml`, strings.NewReader(input))
	if err != nil {
		t.Fatal(err)
	}
	if file.Target == nil || file.Target.Address != "https://Application.Example:443/context/" {
		t.Fatalf("target was not retained: %#v", file.Target)
	}
	if resolved.Target == nil {
		t.Fatal("resolved target is absent")
	}
	if resolved.Target.ConnectTimeout != DefaultConnectTimeout ||
		resolved.Target.ResponseHeaderTimeout != DefaultResponseHeaderTimeout ||
		resolved.Target.RequestTimeout != DefaultRequestTimeout {
		t.Fatalf("unexpected network defaults: %#v", resolved.Target)
	}
	if resolved.Target.CABundlePath != "" || len(resolved.Target.CABundlePEM) != 0 {
		t.Fatalf("unexpected CA bundle: %#v", resolved.Target)
	}
}

func TestDecodeAcceptsExplicitLimitsAndSentinels(t *testing.T) {
	value := "version: 1\nlistener:\n  address: '[::1]:0'\ntrace-workspace:\n  max-bytes: unlimited\n  idle-ttl: never\n"
	_, resolved, err := Decode("config.yaml", strings.NewReader(value))
	if err != nil {
		t.Fatal(err)
	}
	if !resolved.Unlimited || !resolved.NeverExpire {
		t.Fatalf("sentinels not preserved: %#v", resolved)
	}
}

func TestDecodeRejectsUnknownDuplicateAndMultipleDocumentInput(t *testing.T) {
	cases := []string{
		DefaultYAML + "secret: value\n",
		"version: 1\nversion: 1\nlistener:\n  address: 127.0.0.1:1\ntrace-workspace:\n  max-bytes: 1GiB\n  idle-ttl: 1h\n",
		DefaultYAML + "---\nversion: 1\n",
		"version: &version 1\nlistener:\n  address: 127.0.0.1:1\ntrace-workspace:\n  max-bytes: 1GiB\n  idle-ttl: 1h\n",
	}
	for index, value := range cases {
		if _, _, err := Decode("config.yaml", strings.NewReader(value)); err == nil {
			t.Fatalf("case %d accepted", index)
		}
	}
}

func TestDecodeRejectsUnsafeAmbiguousAndOverflowingValues(t *testing.T) {
	for _, values := range [][2]string{
		{"0GiB", "4h"}, {"-1GiB", "4h"}, {"4GB", "4h"}, {"9223372036854775807TiB", "4h"},
		{"4GiB", "0h"}, {"4GiB", "-1h"}, {"4GiB", "1.5h"}, {"4GiB", "3600"},
	} {
		input := "version: 1\nlistener:\n  address: 127.0.0.1:7943\ntrace-workspace:\n  max-bytes: " + values[0] + "\n  idle-ttl: " + values[1] + "\n"
		if _, _, err := Decode("config.yaml", strings.NewReader(input)); err == nil {
			t.Fatalf("accepted max=%q ttl=%q", values[0], values[1])
		}
	}
}

func TestDurationParsingUsesExplicitCanonicalUnits(t *testing.T) {
	duration, never, err := parseDuration("30m")
	if err != nil || never || duration != 30*time.Minute {
		t.Fatalf("duration=%v never=%v err=%v", duration, never, err)
	}
}

func TestConfigSchemaContainsNoSecretFields(t *testing.T) {
	lower := strings.ToLower(DefaultYAML)
	for _, forbidden := range []string{"secret", "credential", "csrf", "cookie", "key:"} {
		if strings.Contains(lower, forbidden) {
			t.Fatalf("default config contains %q", forbidden)
		}
	}
}

func TestDecodeValidatesTargetDurationsAddressAndPresence(t *testing.T) {
	for name, targetYAML := range map[string]string{
		"missing address":  "target: {}\n",
		"padded address":   "target:\n  address: ' https://example.test'\n",
		"never timeout":    "target:\n  address: https://example.test\n  request-timeout: never\n",
		"fraction timeout": "target:\n  address: https://example.test\n  connect-timeout: 1.5s\n",
		"unknown alias":    "target:\n  address: https://example.test\n  timeout: 5s\n",
	} {
		t.Run(name, func(t *testing.T) {
			if _, _, err := Decode("config.yaml", strings.NewReader(DefaultYAML+targetYAML)); err == nil {
				t.Fatal("invalid target configuration was accepted")
			}
		})
	}
}

func TestDecodeResolvesAndValidatesCustomCABundle(t *testing.T) {
	directory := t.TempDir()
	certificatePath := filepath.Join(directory, "private-ca.pem")
	if err := os.WriteFile(certificatePath, testCertificatePEM(t), 0o600); err != nil {
		t.Fatal(err)
	}
	value := DefaultYAML + "target:\n  address: https://example.test\n  ca-bundle: private-ca.pem\n"
	_, resolved, err := Decode(filepath.Join(directory, "config.yaml"), strings.NewReader(value))
	if err != nil {
		t.Fatal(err)
	}
	if resolved.Target == nil || resolved.Target.CABundlePath != certificatePath || len(resolved.Target.CABundlePEM) == 0 {
		t.Fatalf("CA bundle was not resolved: %#v", resolved.Target)
	}
	if err := os.WriteFile(certificatePath, []byte("not a certificate"), 0o600); err != nil {
		t.Fatal(err)
	}
	if _, _, err := Decode(filepath.Join(directory, "config.yaml"), strings.NewReader(value)); err == nil {
		t.Fatal("invalid CA bundle was accepted")
	}
}

func testCertificatePEM(t *testing.T) []byte {
	t.Helper()
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatal(err)
	}
	template := &x509.Certificate{
		SerialNumber: big.NewInt(1), Subject: pkix.Name{CommonName: "loomspan test CA"},
		NotBefore: time.Now().Add(-time.Hour), NotAfter: time.Now().Add(time.Hour),
		IsCA: true, BasicConstraintsValid: true, KeyUsage: x509.KeyUsageCertSign,
	}
	der, err := x509.CreateCertificate(rand.Reader, template, template, &key.PublicKey, key)
	if err != nil {
		t.Fatal(err)
	}
	return pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der})
}
