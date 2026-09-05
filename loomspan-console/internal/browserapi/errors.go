package browserapi

import (
	"bytes"
	"encoding/json"
	"io"
	"net/http"
)

const (
	maxJSONBody       = 1024
	maxTargetJSONBody = 4 * 1024
)

type errorEnvelope struct {
	Error browserError `json:"error"`
}

type browserError struct {
	Code          string `json:"code"`
	Message       string `json:"message"`
	TargetScopeID string `json:"targetScopeId,omitempty"`
	Details       any    `json:"details,omitempty"`
}

func writeError(response http.ResponseWriter, status int, code, message string) {
	ApplyHeaders(response.Header())
	response.Header().Set("Cache-Control", "no-store")
	response.Header().Set("Content-Type", "application/json; charset=utf-8")
	response.WriteHeader(status)
	_ = json.NewEncoder(response).Encode(errorEnvelope{Error: browserError{Code: code, Message: message}})
}

func writeJSON(response http.ResponseWriter, status int, value any) {
	content, err := json.Marshal(value)
	if err != nil {
		writeError(response, http.StatusInternalServerError, "CONSOLE_ERROR", "The Console response could not be created.")
		return
	}
	writeJSONBytes(response, status, append(content, '\n'))
}

func writeJSONBytes(response http.ResponseWriter, status int, content []byte) {
	ApplyHeaders(response.Header())
	response.Header().Set("Cache-Control", "no-store")
	response.Header().Set("Content-Type", "application/json; charset=utf-8")
	response.WriteHeader(status)
	_, _ = response.Write(content)
}

func decodeJSON(request *http.Request, value any) error {
	return decodeJSONLimit(request, value, maxJSONBody)
}

func decodeJSONLimit(request *http.Request, value any, limit int64) error {
	content, err := io.ReadAll(io.LimitReader(request.Body, limit+1))
	if err != nil || int64(len(content)) > limit {
		return staticError("request body exceeds limit")
	}
	decoder := json.NewDecoder(bytes.NewReader(content))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(value); err != nil {
		return err
	}
	var trailing any
	if err := decoder.Decode(&trailing); err != io.EOF {
		return errTrailingJSON
	}
	return nil
}

type staticError string

func (err staticError) Error() string { return string(err) }

const errTrailingJSON = staticError("request body must contain one JSON value")
