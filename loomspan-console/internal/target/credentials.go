package target

import (
	"fmt"
	"net/http"
	"sync"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/applicationclient"
)

type credentialProvider struct {
	mu         sync.Mutex
	generation uint64
	value      []byte
	closed     bool
}

type credentialCapability struct {
	provider   *credentialProvider
	generation uint64
}

func (provider *credentialProvider) install(value []byte) (uint64, error) {
	if err := applicationclient.ValidateCredential(value); err != nil {
		return 0, err
	}
	owned := append([]byte(nil), value...)
	provider.mu.Lock()
	defer provider.mu.Unlock()
	if provider.closed {
		clear(owned)
		return 0, fmt.Errorf("credential provider is closed")
	}
	clear(provider.value)
	provider.generation++
	provider.value = owned
	return provider.generation, nil
}

func (provider *credentialProvider) hasCredential() bool {
	provider.mu.Lock()
	defer provider.mu.Unlock()
	return len(provider.value) > 0 && !provider.closed
}

func (provider *credentialProvider) capability() applicationclient.Credential {
	provider.mu.Lock()
	defer provider.mu.Unlock()
	if provider.closed || len(provider.value) == 0 {
		return nil
	}
	return credentialCapability{provider: provider, generation: provider.generation}
}

func (capability credentialCapability) Apply(request *http.Request) error {
	capability.provider.mu.Lock()
	defer capability.provider.mu.Unlock()
	if capability.provider.closed || capability.generation != capability.provider.generation ||
		len(capability.provider.value) == 0 {
		return fmt.Errorf("application credential is no longer current")
	}
	request.Header.Set(applicationclient.APIKeyHeader, string(capability.provider.value))
	return nil
}

func (provider *credentialProvider) close() {
	provider.mu.Lock()
	defer provider.mu.Unlock()
	clear(provider.value)
	provider.value = nil
	provider.closed = true
	provider.generation++
}

func (provider *credentialProvider) GoString() string {
	provider.mu.Lock()
	defer provider.mu.Unlock()
	return fmt.Sprintf("credentialProvider{generation:%d,present:%t,closed:%t}", provider.generation, len(provider.value) > 0, provider.closed)
}

func (capability credentialCapability) GoString() string {
	return fmt.Sprintf("credentialCapability{generation:%d}", capability.generation)
}
