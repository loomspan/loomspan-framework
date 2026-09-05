//go:build !windows && !darwin && !linux

package browseropen

import "fmt"

func open(string) error {
	return fmt.Errorf("default browser opening is unsupported")
}
