package browseropen

import "fmt"

func Open(url string) error {
	if url == "" {
		return fmt.Errorf("browser URL is required")
	}
	return open(url)
}
