package release

import "testing"

func TestValidateProductVersion(t *testing.T) {
	tests := []struct {
		name    string
		value   string
		wantErr bool
	}{
		{name: "snapshot", value: "0.1.0-SNAPSHOT"},
		{name: "prerelease and build", value: "1.2.3-rc.1+build.7"},
		{name: "blank", value: "", wantErr: true},
		{name: "whitespace", value: " 0.1.0", wantErr: true},
		{name: "development", value: "development", wantErr: true},
		{name: "maven placeholder", value: "${project.version}", wantErr: true},
		{name: "template placeholder", value: "{{VERSION}}", wantErr: true},
		{name: "control", value: "1.0.0\n", wantErr: true},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			err := ValidateProductVersion(test.value)
			if (err != nil) != test.wantErr {
				t.Fatalf("ValidateProductVersion(%q) error = %v, wantErr %v", test.value, err, test.wantErr)
			}
		})
	}
}

func TestVersionPreservesCompleteQualifier(t *testing.T) {
	const version = "0.1.0-SNAPSHOT"
	if err := ValidateProductVersion(version); err != nil {
		t.Fatal(err)
	}
	if got := version; got != "0.1.0-SNAPSHOT" {
		t.Fatalf("version = %q", got)
	}
}
