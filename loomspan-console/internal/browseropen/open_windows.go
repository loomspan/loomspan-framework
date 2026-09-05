//go:build windows

package browseropen

import "os/exec"

func open(url string) error {
	return exec.Command("rundll32.exe", "url.dll,FileProtocolHandler", url).Start()
}
