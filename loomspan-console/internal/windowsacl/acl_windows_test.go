//go:build windows

package windowsacl

import (
	"testing"

	"golang.org/x/sys/windows"
)

func TestGrantsOnlyComparesPrincipalSIDsStructurally(t *testing.T) {
	user, err := windows.StringToSid("S-1-5-21-1-2-3-1001")
	if err != nil {
		t.Fatal(err)
	}
	system, err := windows.CreateWellKnownSid(windows.WinLocalSystemSid)
	if err != nil {
		t.Fatal(err)
	}
	administrators, err := windows.CreateWellKnownSid(windows.WinBuiltinAdministratorsSid)
	if err != nil {
		t.Fatal(err)
	}

	sd, err := windows.SecurityDescriptorFromString(
		"D:P(A;;FA;;;S-1-5-21-1-2-3-1001)(A;;FA;;;S-1-5-18)(A;;FA;;;S-1-5-32-544)",
	)
	if err != nil {
		t.Fatal(err)
	}
	dacl, _, err := sd.DACL()
	if err != nil {
		t.Fatal(err)
	}
	if !GrantsOnly(dacl, user, system, administrators) {
		t.Fatal("equivalent numeric principal SIDs were rejected")
	}

	everyone, err := windows.CreateWellKnownSid(windows.WinWorldSid)
	if err != nil {
		t.Fatal(err)
	}
	if GrantsOnly(dacl, user, system, everyone) {
		t.Fatal("unexpected principal was accepted")
	}
}
