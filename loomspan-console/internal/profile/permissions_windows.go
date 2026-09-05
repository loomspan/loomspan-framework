//go:build windows

package profile

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"syscall"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/windowsacl"
	"golang.org/x/sys/windows"
)

func rejectUnsafePath(path string) error {
	current := filepath.VolumeName(path) + string(filepath.Separator)
	relative := strings.TrimPrefix(path, current)
	for _, part := range strings.Split(relative, string(filepath.Separator)) {
		if part == "" {
			continue
		}
		current = filepath.Join(current, part)
		info, err := os.Lstat(current)
		if err != nil {
			return err
		}
		data, ok := info.Sys().(*syscall.Win32FileAttributeData)
		if !ok {
			return fmt.Errorf("cannot inspect Windows path attributes")
		}
		if data.FileAttributes&windows.FILE_ATTRIBUTE_REPARSE_POINT != 0 {
			return fmt.Errorf("%s is a reparse point", current)
		}
	}
	return nil
}

func resolveSafeDirectory(path string) (string, error) {
	if err := rejectUnsafePath(path); err != nil {
		return "", err
	}
	resolved, err := filepath.EvalSymlinks(path)
	if err != nil {
		return "", err
	}
	absolute, err := filepath.Abs(resolved)
	if err != nil {
		return "", err
	}
	return filepath.Clean(absolute), nil
}

func verifyProtectedDirectory(path string) error {
	info, err := os.Stat(path)
	if err != nil {
		return err
	}
	if !info.IsDir() {
		return fmt.Errorf("not a directory")
	}
	return verifyWindowsProtection(path)
}

func verifyProtectedFile(path string) error {
	info, err := os.Stat(path)
	if err != nil {
		return err
	}
	if !info.Mode().IsRegular() {
		return fmt.Errorf("not a regular file")
	}
	return verifyWindowsProtection(path)
}

func protectNewDirectory(path string) error { return setWindowsProtection(path, true) }
func protectNewFile(path string) error      { return setWindowsProtection(path, false) }

func currentUserSID() (*windows.SID, error) {
	token, err := windows.OpenCurrentProcessToken()
	if err != nil {
		return nil, err
	}
	defer token.Close()
	user, err := token.GetTokenUser()
	if err != nil {
		return nil, err
	}
	return user.User.Sid.Copy()
}

func setWindowsProtection(path string, directory bool) error {
	user, err := currentUserSID()
	if err != nil {
		return err
	}
	flags := ""
	if directory {
		flags = "OICI"
	}
	sd, err := windows.SecurityDescriptorFromString(
		"O:" + user.String() + "D:P(A;" + flags + ";FA;;;" + user.String() + ")(A;" + flags + ";FA;;;SY)(A;" + flags + ";FA;;;BA)",
	)
	if err != nil {
		return err
	}
	owner, _, err := sd.Owner()
	if err != nil {
		return err
	}
	dacl, _, err := sd.DACL()
	if err != nil {
		return err
	}
	return windows.SetNamedSecurityInfo(path, windows.SE_FILE_OBJECT,
		windows.OWNER_SECURITY_INFORMATION|windows.DACL_SECURITY_INFORMATION|windows.PROTECTED_DACL_SECURITY_INFORMATION,
		owner, nil, dacl, nil)
}

func verifyWindowsProtection(path string) error {
	user, err := currentUserSID()
	if err != nil {
		return err
	}
	sd, err := windows.GetNamedSecurityInfo(path, windows.SE_FILE_OBJECT,
		windows.OWNER_SECURITY_INFORMATION|windows.DACL_SECURITY_INFORMATION)
	if err != nil || sd == nil {
		return fmt.Errorf("cannot inspect owner and DACL")
	}
	owner, _, err := sd.Owner()
	if err != nil || !owner.Equals(user) {
		return fmt.Errorf("path is not owned by the current user")
	}
	control, _, err := sd.Control()
	if err != nil || control&windows.SE_DACL_PROTECTED == 0 {
		return fmt.Errorf("path DACL is not protected")
	}
	dacl, _, err := sd.DACL()
	if err != nil {
		return fmt.Errorf("cannot inspect path DACL")
	}
	system, err := windows.CreateWellKnownSid(windows.WinLocalSystemSid)
	if err != nil {
		return err
	}
	administrators, err := windows.CreateWellKnownSid(windows.WinBuiltinAdministratorsSid)
	if err != nil {
		return err
	}
	if !windowsacl.GrantsOnly(dacl, user, system, administrators) {
		return fmt.Errorf("path DACL grants unexpected principals")
	}
	return nil
}
