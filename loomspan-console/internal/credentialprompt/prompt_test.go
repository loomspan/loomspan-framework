package credentialprompt

import (
	"bytes"
	"errors"
	"strings"
	"testing"
)

type fakeTerminal struct {
	fd     uintptr
	output bytes.Buffer
}

func (terminal *fakeTerminal) Fd() uintptr                     { return terminal.fd }
func (terminal *fakeTerminal) Write(value []byte) (int, error) { return terminal.output.Write(value) }

func TestPromptRequiresInteractiveTerminalReadsWithoutEchoAndSanitizesFailures(t *testing.T) {
	input := &fakeTerminal{fd: 1}
	output := &fakeTerminal{fd: 2}
	secret := []byte("LOOMSPAN_" + "TEST_APPLICATION_KEY_DO_NOT_LEAK_123456")
	value, err := read(input, output, functions{
		isTerminal:   func(int) bool { return true },
		readPassword: func(int) ([]byte, error) { return append([]byte(nil), secret...), nil },
	})
	if err != nil || !bytes.Equal(value, secret) {
		t.Fatalf("value was not returned: %v", err)
	}
	if output.output.String() != "Application key: \n" || strings.Contains(output.output.String(), string(secret)) {
		t.Fatalf("unsafe prompt output: %q", output.output.String())
	}
	_, err = read(input, output, functions{
		isTerminal:   func(int) bool { return true },
		readPassword: func(int) ([]byte, error) { return append([]byte(nil), secret...), errors.New(string(secret)) },
	})
	if err == nil || strings.Contains(err.Error(), string(secret)) {
		t.Fatalf("unsafe read failure: %v", err)
	}
}
