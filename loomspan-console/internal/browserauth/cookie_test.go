package browserauth

import (
	"net/http"
	"testing"
)

func TestSessionCookieHasExactPlaintextLoopbackAttributes(t *testing.T) {
	cookie := SessionCookie("value")
	if cookie.Name != SessionCookieName || cookie.Path != "/" || !cookie.HttpOnly ||
		cookie.SameSite != http.SameSiteStrictMode || cookie.Secure ||
		cookie.MaxAge != 0 || !cookie.Expires.IsZero() {
		t.Fatalf("cookie=%#v", cookie)
	}
}
