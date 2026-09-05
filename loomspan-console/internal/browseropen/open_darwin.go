//go:build darwin

package browseropen

import "os/exec"

func open(url string) error {
	return exec.Command("open", url).Start()
}
