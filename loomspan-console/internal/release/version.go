package release

import (
	"fmt"
	"strings"
	"unicode"
)

var productVersion = "development"

func ProductVersion() string {
	return productVersion
}

func ValidateProductVersion(version string) error {
	if version == "" || strings.TrimSpace(version) != version {
		return fmt.Errorf("invalid Loomspan product version %q: value must be nonblank and unpadded", version)
	}
	if version == "development" || strings.Contains(version, "${") || strings.Contains(version, "{{") {
		return fmt.Errorf("invalid Loomspan product version %q: production version was not resolved", version)
	}
	for _, current := range version {
		if unicode.IsControl(current) || unicode.IsSpace(current) {
			return fmt.Errorf("invalid Loomspan product version %q: whitespace and control characters are forbidden", version)
		}
	}
	return nil
}
