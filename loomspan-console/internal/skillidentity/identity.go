// Package skillidentity validates exact portable identities at consumed contract boundaries.
package skillidentity

import (
	"regexp"
	"strings"
)

var componentPattern = regexp.MustCompile(`^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$`)

// ValidNamespace checks the portable component grammar without normalization.
func ValidNamespace(namespace string) bool {
	return len(namespace) <= 64 && componentPattern.MatchString(namespace) && !strings.Contains(namespace, "anthropic") && !strings.Contains(namespace, "claude")
}

// ValidQualified checks the complete identity against its explicit components.
func ValidQualified(qualified, namespace, localName string) bool {
	return ValidNamespace(namespace) && ValidNamespace(localName) && len(namespace)+2+len(localName) <= 64 && qualified == namespace+":"+localName
}

// ValidCapture checks the entry identity against its captured namespace map.
func ValidCapture(skill, namespace, generation string, generations map[string]string) bool {
	owner, localName, found := strings.Cut(skill, ":")
	if !found || owner != namespace || !ValidQualified(skill, owner, localName) || strings.TrimSpace(generation) == "" || len(generations) == 0 || generations[namespace] != generation {
		return false
	}
	for name, id := range generations {
		if !ValidNamespace(name) || strings.TrimSpace(id) == "" {
			return false
		}
	}
	return true
}

// ValidOwner checks optional owner facts without assigning meaning to route strings.
func ValidOwner(namespace, generation string, generations map[string]string) bool {
	return ValidNamespace(namespace) && strings.TrimSpace(generation) != "" && generations[namespace] == generation
}
