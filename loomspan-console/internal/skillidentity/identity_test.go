package skillidentity

import (
	"strings"
	"testing"
)

func TestPortableCapture(t *testing.T) {
	if !ValidCapture("order-support:find-order", "order-support", "g1", map[string]string{"order-support": "g1"}) {
		t.Fatal("valid portable entry identity rejected")
	}
}

func TestPortableComponentsAndPairBoundaries(t *testing.T) {
	for _, name := range []string{"orders", "order-support", "lookup-3pl", strings.Repeat("a", 64)} {
		if !ValidNamespace(name) {
			t.Errorf("valid component %q rejected", name)
		}
	}
	for _, name := range []string{"", "Orders", " orders", "orders ", "3pl", "a_b", "a--b", "a-", "a.b", "a\n", "é", "claude-tool", "myanthropic", strings.Repeat("a", 65)} {
		if ValidNamespace(name) {
			t.Errorf("invalid component %q accepted", name)
		}
	}
	for _, length := range []int{49, 50} {
		local := strings.Repeat("a", length)
		if got := ValidQualified("order-support:"+local, "order-support", local); got != (length == 49) {
			t.Errorf("pair length %d result %t", length, got)
		}
	}
	if ValidQualified("orders:lookup", "warehouse", "lookup") || ValidQualified("orders:lookup:extra", "orders", "lookup:extra") {
		t.Fatal("inconsistent tuple accepted")
	}
}

func TestCaptureRequiresExactOwnerAndGeneration(t *testing.T) {
	generations := map[string]string{"orders": "g1", "warehouse": "g2"}
	for _, skill := range []string{"orders-lookup", "warehouse:lookup", "Orders:lookup", "orders--lookup", "orders:lookup:more"} {
		if ValidCapture(skill, "orders", "g1", generations) {
			t.Errorf("invalid capture %q accepted", skill)
		}
	}
	if ValidCapture("orders:lookup", "orders", "g2", generations) || ValidOwner("orders", "g2", generations) {
		t.Fatal("mismatched generation accepted")
	}
	if !ValidOwner("warehouse", "g2", generations) {
		t.Fatal("valid owner rejected")
	}
	generations["bad_namespace"] = "g3"
	if ValidCapture("orders:lookup", "orders", "g1", generations) {
		t.Fatal("invalid captured owner accepted")
	}
}
