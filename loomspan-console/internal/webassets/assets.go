package webassets

import (
	"embed"
	"io/fs"
)

//go:embed generated/*
var embedded embed.FS

func Embedded() (fs.FS, error) {
	return fs.Sub(embedded, "generated")
}
