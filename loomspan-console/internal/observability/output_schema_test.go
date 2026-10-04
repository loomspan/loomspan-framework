package observability

import (
	"encoding/json"
	"testing"
)

func TestSkillDetailOutputSchemaMetadataPreservesTextAndRejectsMalformedShapes(t *testing.T) {
	skill := SkillDetail{RegisteredName: "parent", Source: "YAML", SourcePath: "parent.yaml", Yaml: "name: parent\noutput_from: {skill: child}"}
	schema := "{\n  \"type\":\"object\",\"description\":\"<script>unchanged</script>\"\n}"
	skill.OutputSchema = &schema
	encoded, err := json.Marshal(skill)
	if err != nil {
		t.Fatal(err)
	}
	var decoded SkillDetail
	if err := json.Unmarshal(encoded, &decoded); err != nil {
		t.Fatal(err)
	}
	if decoded.OutputSchema == nil || *decoded.OutputSchema != schema || decoded.Yaml != skill.Yaml {
		t.Fatalf("metadata changed: %#v", decoded)
	}
	for _, invalid := range []string{"", " ", "null", "[]", "42", "\"text\"", "{bad"} {
		skill.OutputSchema = &invalid
		if validateSkillDetail(skill, "parent") == nil {
			t.Fatalf("accepted outputSchema %q", invalid)
		}
	}
	skill.OutputSchema = nil
	if err := validateSkillDetail(skill, "parent"); err != nil {
		t.Fatal(err)
	}
}

func TestForwardingSkillDetailFixturePreservesDerivedMetadataAndSource(t *testing.T) {
	var detail SkillDetail
	body := readFixture(t, "forwarding-skill-detail.json")
	if err := json.Unmarshal(body, &detail); err != nil {
		t.Fatal(err)
	}
	if detail.OutputSchema == nil || detail.Yaml == "" {
		t.Fatalf("forwarding detail missing schema or original source: %#v", detail)
	}
	if err := validateSkillDetail(detail, detail.RegisteredName); err != nil {
		t.Fatal(err)
	}
	encoded, _ := json.Marshal(detail)
	var restored SkillDetail
	if err := json.Unmarshal(encoded, &restored); err != nil {
		t.Fatal(err)
	}
	if *restored.OutputSchema != *detail.OutputSchema || restored.Yaml != detail.Yaml {
		t.Fatal("metadata or declaration changed")
	}
}
