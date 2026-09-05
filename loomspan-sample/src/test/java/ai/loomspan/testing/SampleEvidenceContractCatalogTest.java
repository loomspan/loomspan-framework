package ai.loomspan.testing;

import ai.loomspan.sample.SampleApplication;

import ai.loomspan.internal.skill.YamlSkillCatalog;
import ai.loomspan.internal.skill.YamlSkillManifest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = SampleApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SampleEvidenceContractCatalogTest
{
    @Autowired
    private YamlSkillCatalog catalog;

    @Test
    void loadsAllFiveMigratedSampleEvidenceContractsThroughTheRealCatalog()
    {
        assertContract("duplicateInvoiceChecker", Map.of(
                "vendorName", "invoiceParser",
                "invoiceDate", "invoiceParser",
                "totalAmount", "invoiceParser",
                "isDuplicate", "invoiceParser and expenseLookup",
                "reasoning", "invoiceParser and expenseLookup"));
        assertContract("handleIncident", Map.of(
                "severity", "classifyIncident",
                "category", "classifyIncident",
                "likelyCause", "classifyIncident and (investigateNetwork or investigateApp)",
                "evidenceSummary", "investigateNetwork or investigateApp",
                "recommendedAction", "investigateNetwork or investigateApp",
                "userMessage", "draftIncidentResponse"));
        assertContract("processClaim", Map.of(
                "disposition", "assessCoverage and fraudScreen and recommendDisposition",
                "payableAmount", "assessCoverage",
                "coverageSummary", "assessCoverage",
                "fraudRisk", "fraudScreen",
                "matchedExclusions", "assessCoverage",
                "rationale", "extractClaimFacts and assessCoverage and fraudScreen and recommendDisposition",
                "evidenceNotes", "extractClaimFacts and assessCoverage and fraudScreen"));
        assertContract("resolveSupportCase", Map.of(
                "intents", "understandIntent",
                "disposition", "understandIntent and (handleBilling or handleTechnical or handleHowTo) and composeReply",
                "refundRecommended", "handleBilling or handleTechnical or handleHowTo",
                "factsSummary", "handleBilling or handleTechnical or handleHowTo",
                "draftReply", "composeReply",
                "internalNotes", "understandIntent and (handleBilling or handleTechnical or handleHowTo) and composeReply"));
        assertContract("planTrip", Map.of(
                "summary", "assembleItinerary",
                "transport", "planTransport",
                "hotel", "planStay",
                "estimatedTotal", "assembleItinerary",
                "rationale", "understandPreferences and planTransport and planStay and assembleItinerary",
                "openQuestions", "understandPreferences and assembleItinerary"));

        assertThat(catalog.getSkill("duplicateInvoiceChecker").concurrencyEnabled()).isTrue();
    }

    @Test
    void loadsTravelSchemasWithRequiredNullableTransportLegs()
    {
        assertTravelTransportContract("assembleItinerary");
        assertTravelTransportContract("planTrip");
    }

    private void assertContract(String skillName, Map<String, String> expected)
    {
        assertThat(catalog.getSkill(skillName)).as(skillName).isNotNull();
        assertThat(catalog.getSkill(skillName).evidenceContract().claims())
                .containsExactlyInAnyOrderElementsOf(expected.keySet());
        expected.forEach((claim, expression) -> assertThat(
                catalog.getSkill(skillName).evidenceContract().canonicalExpressionForClaim(claim))
                .as(skillName + "." + claim)
                .isEqualTo(expression));
    }

    private void assertTravelTransportContract(String skillName)
    {
        YamlSkillManifest.OutputSchemaManifest root = catalog.getSkill(skillName).outputSchema();
        assertThat(root.getRequired()).contains("transport");
        YamlSkillManifest.OutputSchemaManifest transport = root.getProperties().get("transport");
        assertThat(transport.getRequired()).containsExactly("mode", "outbound", "returnLeg");
        assertThat(transport.getAdditionalProperties()).isTrue();

        for (String legName : List.of("outbound", "returnLeg"))
        {
            YamlSkillManifest.OutputSchemaManifest leg = transport.getProperties().get(legName);
            assertThat(leg.getType()).isEqualTo("object");
            assertThat(leg.getNullable()).isTrue();
            assertThat(leg.getAdditionalProperties()).isTrue();
            assertThat(leg.getDescription())
                    .contains("transport digest")
                    .contains("null when unavailable")
                    .contains("Never invent details")
                    .contains("empty object");
        }
    }
}
