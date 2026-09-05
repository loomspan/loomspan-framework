//go:build windows

package windowsacl

import (
	"unsafe"

	"golang.org/x/sys/windows"
)

// GrantsOnly reports whether every ACE is an allow entry for exactly one of
// the supplied principals, with no duplicates or additional principals.
func GrantsOnly(dacl *windows.ACL, allowed ...*windows.SID) bool {
	if dacl == nil || int(dacl.AceCount) != len(allowed) {
		return false
	}
	seen := make([]bool, len(allowed))
	for index := uint32(0); index < uint32(dacl.AceCount); index++ {
		var ace *windows.ACCESS_ALLOWED_ACE
		if err := windows.GetAce(dacl, index, &ace); err != nil || ace == nil ||
			ace.Header.AceType != windows.ACCESS_ALLOWED_ACE_TYPE {
			return false
		}
		sid := (*windows.SID)(unsafe.Pointer(&ace.SidStart))
		matched := false
		for allowedIndex, expected := range allowed {
			if !seen[allowedIndex] && sid.Equals(expected) {
				seen[allowedIndex] = true
				matched = true
				break
			}
		}
		if !matched {
			return false
		}
	}
	return true
}
