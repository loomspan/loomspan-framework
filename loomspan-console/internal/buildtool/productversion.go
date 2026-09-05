package main

import (
	"encoding/xml"
	"fmt"
	"io"
	"os"
	"strings"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/release"
)

func readProductVersion(filename string) (string, error) {
	file, err := os.Open(filename)
	if err != nil {
		return "", fmt.Errorf("open root POM: %w", err)
	}
	defer file.Close()
	decoder := xml.NewDecoder(file)
	depth := 0
	rootProject := false
	var versions []string
	for {
		token, err := decoder.Token()
		if err == io.EOF {
			break
		}
		if err != nil {
			return "", fmt.Errorf("parse root POM: %w", err)
		}
		switch value := token.(type) {
		case xml.StartElement:
			depth++
			if depth == 1 {
				rootProject = value.Name.Local == "project"
			}
			if depth == 2 && value.Name.Local == "version" {
				var version string
				if err := decoder.DecodeElement(&version, &value); err != nil {
					return "", fmt.Errorf("parse direct project version: %w", err)
				}
				depth--
				versions = append(versions, version)
			}
		case xml.EndElement:
			depth--
		}
	}
	if !rootProject {
		return "", fmt.Errorf("root POM document element must be project")
	}
	if len(versions) != 1 {
		return "", fmt.Errorf("root POM must contain exactly one direct project version, found %d", len(versions))
	}
	version := versions[0]
	if strings.TrimSpace(version) != version {
		return "", fmt.Errorf("root POM project version must not contain surrounding whitespace")
	}
	if err := release.ValidateProductVersion(version); err != nil {
		return "", err
	}
	return version, nil
}
