package artifact

import (
	"crypto/rand"
	"encoding/hex"
	"fmt"
)

// handleByteLength is the number of random bytes in an opaque handle. The
// hex-encoded form is 64 characters, which is impractical to guess and carries
// no structure that leaks scope, trace ID, or path.
const handleByteLength = 32

// Handle is an opaque, cryptographically random lookup key for an installed
// artifact. It is never derived from or serialized to a filesystem path, scope
// ID, or trace ID. A handle is valid only within the process and target scope
// that issued it.
type Handle string

// newHandle generates a cryptographically random opaque handle. The entropy
// source is injectable for deterministic tests; production uses crypto/rand.
func newHandle(entropy func() ([]byte, error)) (Handle, error) {
	if entropy == nil {
		entropy = cryptoRandBytes
	}
	data, err := entropy()
	if err != nil {
		return "", fmt.Errorf("generate artifact handle: %w", err)
	}
	return Handle(hex.EncodeToString(data)), nil
}

// cryptoRandBytes reads handleByteLength random bytes from the system CSPRNG.
func cryptoRandBytes() ([]byte, error) {
	data := make([]byte, handleByteLength)
	if _, err := rand.Read(data); err != nil {
		return nil, err
	}
	return data, nil
}

// isValidHandle reports whether a handle string has the exact encoded form
// produced by newHandle. A malformed handle returns INVALID_ARGUMENT; a
// well-formed handle that is not installed in the current scope returns
// ARTIFACT_EXPIRED. This avoids an unbounded removed-handle tombstone set and
// does not reveal whether a random opaque value was previously issued.
func isValidHandle(handle Handle) bool {
	encoded := string(handle)
	if len(encoded) != handleByteLength*2 {
		return false
	}
	for i := 0; i < len(encoded); i++ {
		c := encoded[i]
		if !((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) {
			return false
		}
	}
	return true
}
