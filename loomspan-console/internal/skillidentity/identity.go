// Package skillidentity validates canonical identities at consumed contract boundaries.
package skillidentity

import (
	"regexp"
	"strings"
)

var namespacePattern = regexp.MustCompile(`^[a-z_][a-z0-9_]{0,31}$`)
var skillPattern = regexp.MustCompile(`^[a-z_][a-z0-9_]*-[A-Za-z_][A-Za-z0-9_]*$`)

// ValidCapture checks the entry-scoped ID against its captured namespace map.
func ValidCapture(skill, namespace, generation string, generations map[string]string) bool {
	if len(skill) > 64 || !skillPattern.MatchString(skill) || !namespacePattern.MatchString(namespace) || !strings.HasPrefix(skill, namespace+"-") || strings.TrimSpace(generation) == "" || len(generations) == 0 || generations[namespace] != generation {
		return false
	}
	for name, id := range generations {
		if !namespacePattern.MatchString(name) || strings.TrimSpace(id) == "" {
			return false
		}
	}
	return true
}

// ValidOwner checks optional owner facts without assigning meaning to route strings.
func ValidOwner(namespace, generation string, generations map[string]string) bool {
	return namespacePattern.MatchString(namespace) && strings.TrimSpace(generation) != "" && generations[namespace] == generation
}
