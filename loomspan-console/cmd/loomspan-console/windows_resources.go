package main

// Regenerate the Windows application icon resource after changing release/icon.png.
//go:generate go run github.com/tc-hib/go-winres@v0.3.3 simply --arch amd64 --manifest none --icon ../../release/icon.png --out rsrc
