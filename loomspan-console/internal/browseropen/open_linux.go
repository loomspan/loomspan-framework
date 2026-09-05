//go:build linux

package browseropen

import "os/exec"

func open(url string) error {
	return exec.Command("xdg-open", url).Start()
}
