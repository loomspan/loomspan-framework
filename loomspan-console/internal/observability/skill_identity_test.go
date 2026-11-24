package observability

import (
	"encoding/json"
	"strings"
	"testing"
)

func TestCatalogIdentityTupleIsRequiredAndExact(t *testing.T) {
	for _, owner := range []string{"orders", "warehouse"} {
		body := `{"registeredName":"` + owner + `:lookup","namespace":"` + owner + `","localName":"lookup","kind":"YAML","declaration":"YAML","sourcePath":"Orders import #42"}`
		var summary SkillSummary
		if err := json.Unmarshal([]byte(body), &summary); err != nil {
			t.Fatal(err)
		}
		if summary.RegisteredName != owner+":lookup" || summary.Namespace != owner || summary.LocalName != "lookup" {
			t.Fatalf("identity changed: %#v", summary)
		}
		for _, invalid := range []string{
			strings.Replace(body, `"namespace":"`+owner+`",`, "", 1),
			strings.Replace(body, `"localName":"lookup",`, "", 1),
			strings.Replace(body, `"namespace":"`+owner+`"`, `"namespace":"other"`, 1),
			strings.Replace(body, `"localName":"lookup"`, `"localName":"Lookup"`, 1),
		} {
			if json.Unmarshal([]byte(invalid), &SkillSummary{}) == nil {
				t.Fatalf("accepted invalid tuple %s", invalid)
			}
		}
	}
}
