package applicationclient

import (
	"fmt"
	"net"
	"net/url"
	"strconv"
	"strings"
)

const MaxAddressBytes = 2048

type Address struct {
	display           string
	scheme            string
	authority         string
	contextPath       string
	observabilityRoot string
}

func NormalizeAddress(value string) (Address, error) {
	if value == "" || len(value) > MaxAddressBytes || value != strings.TrimSpace(value) {
		return Address{}, fmt.Errorf("target address must be nonblank, unpadded, and at most %d bytes", MaxAddressBytes)
	}
	if !isASCII(value) || strings.Contains(value, "\\") {
		return Address{}, fmt.Errorf("target address contains an unsafe authority or path")
	}
	parsed, err := url.Parse(value)
	if err != nil || parsed.Opaque != "" || (parsed.Scheme != "http" && parsed.Scheme != "https") {
		return Address{}, fmt.Errorf("target address must be a hierarchical HTTP or HTTPS URL")
	}
	if parsed.User != nil || parsed.RawQuery != "" || parsed.ForceQuery || parsed.Fragment != "" {
		return Address{}, fmt.Errorf("target address must not contain credentials, a query, or a fragment")
	}
	if strings.Contains(parsed.Host, "%") || parsed.Host == "" {
		return Address{}, fmt.Errorf("target address authority is invalid")
	}
	host := parsed.Hostname()
	if host == "" || strings.Contains(host, "%") || !isASCII(host) {
		return Address{}, fmt.Errorf("target address host is invalid")
	}
	port := parsed.Port()
	if port != "" {
		number, err := strconv.ParseUint(port, 10, 16)
		if err != nil || number == 0 {
			return Address{}, fmt.Errorf("target address port is invalid")
		}
		port = strconv.FormatUint(number, 10)
	}
	if ip := net.ParseIP(host); ip != nil {
		host = ip.String()
	} else {
		host = strings.ToLower(host)
		if strings.HasPrefix(host, ".") || strings.HasSuffix(host, ".") || strings.Contains(host, "..") {
			return Address{}, fmt.Errorf("target address DNS name is invalid")
		}
		for _, label := range strings.Split(host, ".") {
			if label == "" || strings.HasPrefix(label, "-") || strings.HasSuffix(label, "-") {
				return Address{}, fmt.Errorf("target address DNS name is invalid")
			}
			for _, current := range label {
				if !(current >= 'a' && current <= 'z') && !(current >= '0' && current <= '9') && current != '-' {
					return Address{}, fmt.Errorf("target address DNS name is invalid")
				}
			}
		}
	}
	if (parsed.Scheme == "http" && port == "80") || (parsed.Scheme == "https" && port == "443") {
		port = ""
	}
	authority := host
	if strings.Contains(host, ":") {
		authority = "[" + host + "]"
	}
	if port != "" {
		authority = net.JoinHostPort(host, port)
	}
	path, err := normalizeContextPath(parsed.EscapedPath(), parsed.RawPath)
	if err != nil {
		return Address{}, err
	}
	scheme := strings.ToLower(parsed.Scheme)
	display := scheme + "://" + authority + path
	return Address{
		display: display, scheme: scheme, authority: authority, contextPath: path,
		observabilityRoot: display + "/_loomspan/observability/v1",
	}, nil
}

func normalizeContextPath(escaped, raw string) (string, error) {
	if raw != "" && raw != escaped {
		return "", fmt.Errorf("target address path encoding is ambiguous")
	}
	if escaped == "" || escaped == "/" {
		return "", nil
	}
	lower := strings.ToLower(escaped)
	if strings.Contains(lower, "%2f") || strings.Contains(lower, "%5c") ||
		strings.Contains(escaped, "//") || !strings.HasPrefix(escaped, "/") {
		return "", fmt.Errorf("target address path contains an unsafe separator")
	}
	segments := strings.Split(strings.TrimSuffix(escaped, "/"), "/")[1:]
	for _, segment := range segments {
		if segment == "" || segment == "." || segment == ".." ||
			strings.EqualFold(segment, "%2e") || strings.EqualFold(segment, "%2e%2e") ||
			strings.Contains(segment, "%") {
			return "", fmt.Errorf("target address path must be clean and unescaped")
		}
	}
	return "/" + strings.Join(segments, "/"), nil
}

func isASCII(value string) bool {
	for index := 0; index < len(value); index++ {
		if value[index] >= 0x80 || value[index] < 0x20 {
			return false
		}
	}
	return true
}

func (address Address) String() string            { return address.display }
func (address Address) Scheme() string            { return address.scheme }
func (address Address) Authority() string         { return address.authority }
func (address Address) ContextPath() string       { return address.contextPath }
func (address Address) ObservabilityRoot() string { return address.observabilityRoot }
func (address Address) Unencrypted() bool         { return address.scheme == "http" }
func (address Address) Equal(other Address) bool  { return address.display == other.display }
func (address Address) InstanceEndpoint() string  { return address.observabilityRoot + "/instance" }
func (address Address) SkillsEndpoint() string    { return address.observabilityRoot + "/skills" }
func (address Address) SkillEndpoint(registeredName string) string {
	return address.observabilityRoot + "/skills/" + url.PathEscape(registeredName)
}
func (address Address) ActiveExecutionsEndpoint() string {
	return address.observabilityRoot + "/active-executions"
}
func (address Address) ActiveExecutionEndpoint(sessionId string) string {
	return address.observabilityRoot + "/active-executions/" + url.PathEscape(sessionId)
}
func (address Address) ActivityEndpoint(instanceID, afterCursor string) string {
	params := url.Values{}
	params.Set("instanceId", instanceID)
	params.Set("afterCursor", afterCursor)
	return address.observabilityRoot + "/activity?" + params.Encode()
}
func (address Address) TracesEndpoint() string { return address.observabilityRoot + "/traces" }
func (address Address) TraceEndpoint(traceId string) string {
	return address.observabilityRoot + "/traces/" + url.PathEscape(traceId)
}
func (address Address) ArtifactEndpoint(traceId string) string {
	return address.observabilityRoot + "/traces/" + url.PathEscape(traceId) + "/artifact"
}
