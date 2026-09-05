package browserapi

import (
	"fmt"
	"net"
	"net/http"
	"net/url"
	"strings"
)

type Policy struct {
	allowed map[string]string
}

func NewPolicy(authority, productionOrigin, developmentOrigin string) (Policy, error) {
	if authority == "" || productionOrigin != "http://"+authority {
		return Policy{}, fmt.Errorf("production authority and origin do not match")
	}
	policy := Policy{allowed: map[string]string{authority: productionOrigin}}
	if developmentOrigin != "" {
		parsedAuthority, parsedOrigin, err := ParseLoopbackOrigin(developmentOrigin)
		if err != nil {
			return Policy{}, err
		}
		policy.allowed[parsedAuthority] = parsedOrigin
	}
	return policy, nil
}

func ParseLoopbackOrigin(value string) (string, string, error) {
	if value == "" || value != strings.TrimSpace(value) || strings.Contains(value, ",") || strings.HasSuffix(value, "/") {
		return "", "", fmt.Errorf("development origin must be an exact HTTP loopback origin")
	}
	parsed, err := url.Parse(value)
	if err != nil || parsed.Scheme != "http" || parsed.User != nil || parsed.Path != "" || parsed.RawQuery != "" || parsed.Fragment != "" {
		return "", "", fmt.Errorf("development origin must be an exact HTTP loopback origin")
	}
	host, port, err := net.SplitHostPort(parsed.Host)
	if err != nil || port == "" || strings.Contains(host, "%") {
		return "", "", fmt.Errorf("development origin must include an explicit loopback IP and port")
	}
	ip := net.ParseIP(host)
	if ip == nil || !ip.IsLoopback() {
		return "", "", fmt.Errorf("development origin must use an explicit loopback IP")
	}
	authority := net.JoinHostPort(ip.String(), port)
	origin := "http://" + authority
	if value != origin {
		return "", "", fmt.Errorf("development origin must use canonical syntax")
	}
	return authority, origin, nil
}

func (policy Policy) ValidateHost(request *http.Request) bool {
	host := request.Host
	if host == "" || host != strings.TrimSpace(host) || strings.ContainsAny(host, ", \t\r\n") {
		return false
	}
	_, ok := policy.allowed[host]
	return ok
}

func (policy Policy) ValidateOrigin(request *http.Request) bool {
	values := request.Header.Values("Origin")
	if len(values) != 1 || values[0] == "" || strings.Contains(values[0], ",") {
		return false
	}
	return policy.allowed[request.Host] == values[0]
}

// ValidateDownloadRequest validates a GET download navigation request. Unlike
// ValidateOrigin, it accepts same-origin browser navigations that do not send
// an Origin header (e.g. clicking an <a download> link). It rejects cross-site
// and same-site fetch metadata and requires navigation mode when Fetch Metadata
// is present. The SameSite=Strict session cookie provides the primary
// cross-site boundary; this check prevents the no-CSRF attachment route from
// becoming a script-readable fetch endpoint.
func (policy Policy) ValidateDownloadRequest(request *http.Request) bool {
	site := request.Header.Get("Sec-Fetch-Site")
	switch site {
	case "cross-site", "same-site":
		return false
	case "same-origin", "none":
		if request.Header.Get("Sec-Fetch-Mode") != "navigate" {
			return false
		}
		dest := request.Header.Get("Sec-Fetch-Dest")
		if dest != "" && dest != "document" && dest != "empty" {
			return false
		}
		user := request.Header.Get("Sec-Fetch-User")
		return user == "" || user == "?1"
	}
	// Sec-Fetch-Site absent (older browsers): only the legacy navigation shape
	// without Origin is accepted. If partial Fetch Metadata is present it must
	// still describe a navigation.
	if mode := request.Header.Get("Sec-Fetch-Mode"); mode != "" && mode != "navigate" {
		return false
	}
	if dest := request.Header.Get("Sec-Fetch-Dest"); dest != "" && dest != "document" && dest != "empty" {
		return false
	}
	return len(request.Header.Values("Origin")) == 0
}
