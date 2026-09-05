package target

import (
	"fmt"
	"strings"
	"testing"
)

func TestCredentialProviderInstallsClearsAndNeverExposesOwnedSecret(t *testing.T) {
	provider := &credentialProvider{}
	secret := []byte("LOOMSPAN_" + "TEST_APPLICATION_KEY_DO_NOT_LEAK_123456")
	first, err := provider.install(secret)
	if err != nil {
		t.Fatal(err)
	}
	second, err := provider.install(secret)
	if err != nil {
		t.Fatal(err)
	}
	if second == first {
		t.Fatal("identical replacement did not create a new generation")
	}
	for _, value := range []string{fmt.Sprintf("%#v", provider), fmt.Sprintf("%#v", provider.capability())} {
		if strings.Contains(value, string(secret)) {
			t.Fatal("credential formatting exposed the secret")
		}
	}
	provider.close()
	if provider.hasCredential() {
		t.Fatal("closed provider retained a credential")
	}
}
