package applicationclient

import (
	"encoding/json"
	"net/http"
	"strings"
)

func mapProblem(status int, contentType string, body []byte) error {
	if status >= 300 && status < 400 {
		return newFailure(FailureUnavailable, CategoryRedirect, nil)
	}
	var problem struct {
		Status  int    `json:"status"`
		Code    string `json:"code"`
		Message string `json:"message"`
	}
	recognized := strings.HasPrefix(strings.ToLower(contentType), "application/json") &&
		json.Unmarshal(body, &problem) == nil && problem.Status == status &&
		problem.Code != "" && problem.Message != ""
	if recognized {
		switch problem.Code {
		case "LOOMSPAN_API_KEY_REJECTED":
			return newFailure(FailureAuthentication, "", nil)
		case "INVALID_REQUEST":
			return newFailure(FailureInvalidArgument, "", nil)
		case "INVALID_CURSOR":
			return newFailure(FailureInvalidCursor, "", nil)
		case "STALE_CURSOR":
			return newFailure(FailureStaleCursor, "", nil)
		case "NOT_FOUND":
			return newFailure(FailureNotFound, "", nil)
		case "LIMIT_EXCEEDED":
			return newFailure(FailureLimitExceeded, "", nil)
		case "LIVE_MONITORING_UNAVAILABLE":
			return newFailure(FailureLiveMonitoringUnavailable, "", nil)
		case "APPLICATION_ERROR":
			failure := newFailure(FailureUnavailable, CategoryUpstreamServer, nil)
			failure.Retryable = true
			return failure
		}
	}
	if status == http.StatusUnauthorized || status == http.StatusForbidden {
		return newFailure(FailureAccess, "", nil)
	}
	if status == http.StatusNotFound {
		return newFailure(FailureUnavailable, CategoryNamespaceNotFound, nil)
	}
	if status >= 500 {
		failure := newFailure(FailureUnavailable, CategoryUpstreamServer, nil)
		failure.Retryable = true
		return failure
	}
	return protocolFailure()
}
