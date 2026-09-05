package applicationclient

import "io"

// NewTestArtifactStream creates an ArtifactStream from a raw reader for use in
// tests that need to exercise consumers of OpenArtifact without a real HTTP
// server. Production code must use Client.OpenArtifact.
func NewTestArtifactStream(body io.ReadCloser, instanceID string, declaredLength int64) *ArtifactStream {
	return &ArtifactStream{
		body:           body,
		instanceID:     instanceID,
		mediaType:      ArtifactMediaType,
		declaredLength: declaredLength,
	}
}
