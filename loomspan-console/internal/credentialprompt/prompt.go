package credentialprompt

import (
	"fmt"
	"os"

	"golang.org/x/term"
)

type terminal interface {
	Fd() uintptr
	Write([]byte) (int, error)
}

type functions struct {
	isTerminal   func(int) bool
	readPassword func(int) ([]byte, error)
}

func Read(input *os.File, output *os.File) ([]byte, error) {
	return read(input, output, functions{isTerminal: term.IsTerminal, readPassword: term.ReadPassword})
}

func read(input terminal, output terminal, calls functions) ([]byte, error) {
	if input == nil || output == nil || calls.isTerminal == nil || calls.readPassword == nil {
		return nil, fmt.Errorf("interactive application-key prompt is unavailable")
	}
	if !calls.isTerminal(int(input.Fd())) || !calls.isTerminal(int(output.Fd())) {
		return nil, fmt.Errorf("application-key prompt requires an interactive terminal")
	}
	if _, err := output.Write([]byte("Application key: ")); err != nil {
		return nil, fmt.Errorf("application-key prompt could not be displayed")
	}
	value, err := calls.readPassword(int(input.Fd()))
	_, newlineErr := output.Write([]byte("\n"))
	if err != nil {
		clear(value)
		return nil, fmt.Errorf("application key could not be read")
	}
	if newlineErr != nil {
		clear(value)
		return nil, fmt.Errorf("application-key prompt could not be completed")
	}
	return value, nil
}
