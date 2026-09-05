package main

import (
	"fmt"
	"regexp"
	"strings"
)

const (
	requiredGo   = "1.26.5"
	requiredNode = "24.18.0"
	requiredNPM  = "12.0.2"
)

var goVersionPattern = regexp.MustCompile(`^go version go([0-9]+\.[0-9]+\.[0-9]+) (?:[^\s]+)$`)

func validateToolchainVersions(goOutput, nodeOutput, npmOutput string) error {
	match := goVersionPattern.FindStringSubmatch(strings.TrimSpace(goOutput))
	if len(match) != 2 || match[1] != requiredGo {
		return fmt.Errorf("Go %s is required; got %q", requiredGo, strings.TrimSpace(goOutput))
	}
	node := strings.TrimSpace(nodeOutput)
	if node != "v"+requiredNode {
		return fmt.Errorf("Node.js %s is required; got %q", requiredNode, node)
	}
	npm := strings.TrimSpace(npmOutput)
	if npm != requiredNPM {
		return fmt.Errorf("npm %s is required; got %q", requiredNPM, npm)
	}
	return nil
}
