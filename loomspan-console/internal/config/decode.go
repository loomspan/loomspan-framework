package config

import (
	"bytes"
	"fmt"
	"io"

	"go.yaml.in/yaml/v4"
)

const maxConfigBytes = 64 * 1024

func Decode(path string, reader io.Reader) (File, Resolved, error) {
	content, err := io.ReadAll(io.LimitReader(reader, maxConfigBytes+1))
	if err != nil {
		return File{}, Resolved{}, fmt.Errorf("read configuration %s", path)
	}
	if len(content) == 0 {
		return File{}, Resolved{}, fmt.Errorf("configuration %s is empty", path)
	}
	if len(content) > maxConfigBytes {
		return File{}, Resolved{}, fmt.Errorf("configuration %s exceeds %d bytes", path, maxConfigBytes)
	}

	var document yaml.Node
	nodeDecoder := yaml.NewDecoder(bytes.NewReader(content))
	if err := nodeDecoder.Decode(&document); err != nil {
		return File{}, Resolved{}, fmt.Errorf("configuration %s is invalid: %s", path, boundedYAMLError(err))
	}
	if len(document.Content) != 1 || document.Content[0].Kind != yaml.MappingNode {
		return File{}, Resolved{}, fmt.Errorf("configuration %s must contain one mapping document", path)
	}
	if err := validateNode(document.Content[0], 0); err != nil {
		return File{}, Resolved{}, fmt.Errorf("configuration %s is invalid: %w", path, err)
	}
	if err := validateSchema(document.Content[0], map[string]map[string]struct{}{
		"":                {"version": {}, "listener": {}, "trace-workspace": {}, "target": {}},
		"listener":        {"address": {}},
		"trace-workspace": {"max-bytes": {}, "idle-ttl": {}},
		"target": {
			"address": {}, "connect-timeout": {}, "response-header-timeout": {},
			"request-timeout": {}, "ca-bundle": {},
		},
	}, ""); err != nil {
		return File{}, Resolved{}, fmt.Errorf("configuration %s is invalid: %w", path, err)
	}
	var extra yaml.Node
	if err := nodeDecoder.Decode(&extra); err != io.EOF {
		if err == nil {
			return File{}, Resolved{}, fmt.Errorf("configuration %s must contain exactly one document", path)
		}
		return File{}, Resolved{}, fmt.Errorf("configuration %s is invalid: %s", path, boundedYAMLError(err))
	}

	var file File
	decoder := yaml.NewDecoder(bytes.NewReader(content))
	decoder.KnownFields(true)
	if err := decoder.Decode(&file); err != nil {
		return File{}, Resolved{}, fmt.Errorf("configuration %s is invalid: %s", path, boundedYAMLError(err))
	}
	resolved, err := file.resolve(path)
	if err != nil {
		return File{}, Resolved{}, fmt.Errorf("configuration %s: %w", path, err)
	}
	return file, resolved, nil
}

func validateNode(node *yaml.Node, depth int) error {
	if depth > 16 {
		return fmt.Errorf("nesting exceeds 16 levels")
	}
	if node.Kind == yaml.AliasNode || node.Anchor != "" {
		return fmt.Errorf("aliases and anchors are not allowed")
	}
	if node.Kind == yaml.MappingNode {
		seen := make(map[string]struct{}, len(node.Content)/2)
		for index := 0; index < len(node.Content); index += 2 {
			key := node.Content[index]
			if key.Kind != yaml.ScalarNode {
				return fmt.Errorf("mapping keys must be strings")
			}
			if _, exists := seen[key.Value]; exists {
				return fmt.Errorf("contains a duplicate field")
			}
			seen[key.Value] = struct{}{}
		}
	}
	for _, child := range node.Content {
		if err := validateNode(child, depth+1); err != nil {
			return err
		}
	}
	return nil
}

func validateSchema(node *yaml.Node, schema map[string]map[string]struct{}, section string) error {
	allowed := schema[section]
	for index := 0; index < len(node.Content); index += 2 {
		key := node.Content[index]
		value := node.Content[index+1]
		if _, ok := allowed[key.Value]; !ok {
			return fmt.Errorf("contains an unknown field")
		}
		nextSection := ""
		switch key.Value {
		case "listener", "trace-workspace", "target":
			nextSection = key.Value
			if value.Kind != yaml.MappingNode {
				return fmt.Errorf("%s must be a mapping", key.Value)
			}
		default:
			if value.Kind != yaml.ScalarNode {
				return fmt.Errorf("%s must be a scalar value", key.Value)
			}
		}
		if nextSection != "" {
			if err := validateSchema(value, schema, nextSection); err != nil {
				return err
			}
		}
	}
	return nil
}

func boundedYAMLError(err error) string {
	_ = err
	return "YAML syntax is invalid"
}
