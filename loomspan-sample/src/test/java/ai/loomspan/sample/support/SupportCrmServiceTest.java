package ai.loomspan.sample.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SupportCrmServiceTest {

    private SupportCrmService service;

    @BeforeEach
    void setUp() {
        service = new SupportCrmService();
    }

    @Test
    void billingDuplicateChargeReturnsDuplicateInvoicesAndGoodwillFacts() {
        Map<String, Object> customer = service.lookupCustomer("billing-duplicate-charge", null);
        Map<String, Object> invoices = service.lookupInvoices("billing-duplicate-charge", null);
        Map<String, Object> policy = service.lookupRefundPolicy("billing-duplicate-charge");

        assertThat(customer.get("priorComplaintCount")).isEqualTo(0);
        assertThat(((Number) customer.get("tenureMonths")).intValue()).isGreaterThan(0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> invoiceList = (List<Map<String, Object>>) invoices.get("invoices");
        assertThat(invoiceList).hasSize(2);
        assertThat(invoiceList.get(0).get("amount")).isEqualTo(invoiceList.get(1).get("amount"));
        assertThat(policy.get("goodwillEligible")).isEqualTo(true);
        assertThat(policy.get("firstComplaintEligible")).isEqualTo(true);
        assertThat(((Number) policy.get("maxGoodwillAmount")).intValue()).isEqualTo(50);
    }

    @Test
    void angryGoodwillPolicyFactsAreStable() {
        Map<String, Object> customer = service.lookupCustomer("angry-goodwill", "CUST-1005");
        Map<String, Object> policy = service.lookupRefundPolicy("angry-goodwill");
        Map<String, Object> invoices = service.lookupInvoices("angry-goodwill", "CUST-1005");

        assertThat(customer.get("priorComplaintCount")).isEqualTo(0);
        assertThat(customer.get("customerId")).isEqualTo("CUST-1005");
        assertThat(policy.get("goodwillEligible")).isEqualTo(true);
        assertThat(policy.get("firstComplaintEligible")).isEqualTo(true);
        assertThat(((Number) policy.get("maxGoodwillAmount")).intValue()).isEqualTo(50);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> invoiceList = (List<Map<String, Object>>) invoices.get("invoices");
        assertThat(invoiceList).isNotEmpty();
        assertThat(((Number) invoiceList.getFirst().get("amount")).doubleValue()).isEqualTo(54.0);
    }

    @Test
    void techCrashSupportsKnownIssueAndDeterministicTicket() {
        Map<String, Object> account = service.lookupAccountStatus("tech-crash-on-checkout");
        Map<String, Object> issues = service.searchKnownIssues("tech-crash-on-checkout");
        Map<String, Object> ticket1 = service.createBugTicket("tech-crash-on-checkout", "checkout crash");
        Map<String, Object> ticket2 = service.createBugTicket("tech-crash-on-checkout", "different summary");

        assertThat(account.get("status")).isEqualTo("active");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> issueList = (List<Map<String, Object>>) issues.get("issues");
        assertThat(issueList).isNotEmpty();
        assertThat(ticket1.get("ticketId")).isEqualTo("BUG-TECH-1002");
        assertThat(ticket2.get("ticketId")).isEqualTo("BUG-TECH-1002");
        assertThat(ticket1.get("summary")).isEqualTo("checkout crash");
        assertThat(ticket2.get("summary")).isEqualTo("different summary");
    }

    @Test
    void mixedScenarioPopulatesBillingAndTechData() {
        Map<String, Object> invoices = service.lookupInvoices("mixed-billing-and-crash", null);
        Map<String, Object> account = service.lookupAccountStatus("mixed-billing-and-crash");
        Map<String, Object> ticket = service.createBugTicket("mixed-billing-and-crash", null);

        @SuppressWarnings("unchecked")
        List<?> invoiceList = (List<?>) invoices.get("invoices");
        assertThat(invoiceList).hasSize(2);
        assertThat(account.get("status")).isEqualTo("active");
        assertThat(ticket.get("ticketId")).isEqualTo("BUG-TECH-1003");
    }

    @Test
    void howToExportReturnsHelpArticlesWithoutRefundPath() {
        Map<String, Object> help = service.searchHelpCenter("how-to-export", "export csv");
        Map<String, Object> policy = service.lookupRefundPolicy("how-to-export");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> articles = (List<Map<String, Object>>) help.get("articles");
        assertThat(articles).isNotEmpty();
        assertThat(articles.getFirst().get("title").toString()).containsIgnoringCase("CSV");
        assertThat(policy.get("goodwillEligible")).isEqualTo(false);
    }

    @Test
    void createBugTicketIsDeterministicAndStateless() {
        Map<String, Object> a = service.createBugTicket("tech-crash-on-checkout", "a");
        Map<String, Object> b = service.createBugTicket("tech-crash-on-checkout", "b");
        Map<String, Object> mixed = service.createBugTicket("mixed-billing-and-crash", "m");

        assertThat(a.get("ticketId")).isEqualTo(b.get("ticketId")).isEqualTo("BUG-TECH-1002");
        assertThat(mixed.get("ticketId")).isEqualTo("BUG-TECH-1003");
        assertThat(a.get("status")).isEqualTo("open");
    }

    @Test
    void unknownScenarioReturnsNeutralValidDataWithoutThrowing() {
        String unknown = "unknown-key-xyz";

        assertThatCode(() -> {
            assertThat(service.lookupCustomer(unknown, null)).isNotEmpty();
            assertThat(service.lookupInvoices(unknown, null)).isNotEmpty();
            assertThat(service.lookupRefundPolicy(unknown)).isNotEmpty();
            assertThat(service.lookupAccountStatus(unknown)).isNotEmpty();
            assertThat(service.searchKnownIssues(unknown)).isNotEmpty();
            assertThat(service.createBugTicket(unknown, null)).isNotEmpty();
            assertThat(service.searchHelpCenter(unknown, null)).isNotEmpty();
        }).doesNotThrowAnyException();

        assertThat(service.lookupRefundPolicy(unknown).get("goodwillEligible")).isEqualTo(false);
        List<?> invoices = (List<?>) service.lookupInvoices(unknown, null).get("invoices");
        assertThat(invoices).isEmpty();
        List<?> issues = (List<?>) service.searchKnownIssues(unknown).get("issues");
        assertThat(issues).isEmpty();
        List<?> articles = (List<?>) service.searchHelpCenter(unknown, null).get("articles");
        assertThat(articles).isEmpty();
        assertThat(service.lookupAccountStatus(unknown).get("status")).isEqualTo("active");
    }
}
