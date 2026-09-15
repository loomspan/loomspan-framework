package ai.loomspan.internal.observability.web;

import ai.loomspan.internal.runtime.trace.CompletionGraceRetention;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObservabilityArtifactDeliveryTest
{
    @Test
    void rejectsNinthDownloadWithoutQueuingAndReclaimsSlot()
    {
        try (ObservabilityArtifactDelivery delivery = new ObservabilityArtifactDelivery())
        {
            List<ObservabilityArtifactDelivery.Admission> admitted = new ArrayList<>();
            for (int index = 0; index < ObservabilityDeliveryLimits.OPEN_ARTIFACT_DOWNLOADS; index++)
            {
                admitted.add(delivery.admit());
            }
            assertThat(delivery.admittedCount()).isEqualTo(8);
            assertThatThrownBy(delivery::admit)
                    .isInstanceOf(ObservabilityException.class)
                    .satisfies(failure -> assertThat(
                            ((ObservabilityException) failure).problem().code())
                            .isEqualTo(ObservabilityProblem.Code.LIMIT_EXCEEDED));

            admitted.getFirst().close();
            admitted.getFirst().close();
            assertThat(delivery.admit()).isNotNull();
            assertThat(delivery.admittedCount()).isEqualTo(8);
        }
    }

    @Test
    void shutdownRejectsNewAdmissionAndReleasesReservations()
    {
        ObservabilityArtifactDelivery delivery = new ObservabilityArtifactDelivery();
        delivery.admit();
        delivery.close();
        assertThat(delivery.admittedCount()).isZero();
        assertThatThrownBy(delivery::admit).isInstanceOf(ObservabilityException.class);
    }

    @Test
    void executorRejectionHappensBeforeArtifactHeadersAndReleasesOwnership() throws Exception
    {
        ExecutorService executor = mock(ExecutorService.class);
        when(executor.submit(any(Runnable.class))).thenThrow(new RejectedExecutionException("closed"));
        ObservabilityArtifactDelivery delivery = new ObservabilityArtifactDelivery(executor);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        AsyncContext async = mock(AsyncContext.class);
        when(request.startAsync(request, response)).thenReturn(async);
        when(response.getOutputStream()).thenReturn(mock(ServletOutputStream.class));
        CompletionGraceRetention.ArtifactLease lease =
                mock(CompletionGraceRetention.ArtifactLease.class);
        Runnable prepareResponse = mock(Runnable.class);
        ObservabilityArtifactDelivery.Admission admission = delivery.admit();

        assertThatThrownBy(() ->
                delivery.open(request, response, admission, lease, prepareResponse))
                .isInstanceOf(RejectedExecutionException.class);

        verify(prepareResponse, never()).run();
        verify(lease).close();
        verify(async).complete();
        assertThat(delivery.admittedCount()).isZero();
        delivery.close();
    }

    @Test
    void asyncSetupFailureReleasesOwnershipBeforeArtifactHeaders() throws Exception
    {
        ObservabilityArtifactDelivery delivery = new ObservabilityArtifactDelivery();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.startAsync(request, response))
                .thenThrow(new IllegalStateException("async unavailable"));
        CompletionGraceRetention.ArtifactLease lease =
                mock(CompletionGraceRetention.ArtifactLease.class);
        Runnable prepareResponse = mock(Runnable.class);
        ObservabilityArtifactDelivery.Admission admission = delivery.admit();

        assertThatThrownBy(() ->
                delivery.open(request, response, admission, lease, prepareResponse))
                .isInstanceOf(IllegalStateException.class);

        verify(prepareResponse, never()).run();
        verify(lease).close();
        assertThat(delivery.admittedCount()).isZero();
        delivery.close();
    }
}
