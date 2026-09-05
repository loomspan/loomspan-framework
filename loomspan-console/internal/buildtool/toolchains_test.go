package main

import "testing"

func TestValidateToolchainVersionsRequiresExactPatches(t *testing.T) {
	if err := validateToolchainVersions(
		"go version go1.26.5 windows/amd64",
		"v24.18.0",
		"12.0.2",
	); err != nil {
		t.Fatal(err)
	}
	tests := []struct {
		name string
		goV  string
		node string
		npm  string
	}{
		{name: "go patch", goV: "go version go1.26.4 windows/amd64", node: "v24.18.0", npm: "12.0.2"},
		{name: "node patch", goV: "go version go1.26.5 linux/amd64", node: "v24.17.0", npm: "12.0.2"},
		{name: "npm patch", goV: "go version go1.26.5 darwin/arm64", node: "v24.18.0", npm: "12.0.1"},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			if err := validateToolchainVersions(test.goV, test.node, test.npm); err == nil {
				t.Fatal("validateToolchainVersions() accepted a mismatch")
			}
		})
	}
}
