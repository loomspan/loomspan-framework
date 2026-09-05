package browserauth

import (
	"crypto/rand"
	"crypto/subtle"
	"encoding/base64"
	"fmt"
	"io"
)

const secretBytes = 32

func Generate(reader io.Reader) (string, error) {
	if reader == nil {
		reader = rand.Reader
	}
	value := make([]byte, secretBytes)
	if _, err := io.ReadFull(reader, value); err != nil {
		return "", fmt.Errorf("generate browser credential: %w", err)
	}
	return base64.RawURLEncoding.EncodeToString(value), nil
}

func compareSecret(expected []byte, candidate string) bool {
	decoded, err := base64.RawURLEncoding.DecodeString(candidate)
	if err != nil || len(decoded) != secretBytes || len(expected) != secretBytes {
		return false
	}
	return subtle.ConstantTimeCompare(expected, decoded) == 1
}

func decodeSecret(value string) ([]byte, bool) {
	decoded, err := base64.RawURLEncoding.DecodeString(value)
	return decoded, err == nil && len(decoded) == secretBytes
}
