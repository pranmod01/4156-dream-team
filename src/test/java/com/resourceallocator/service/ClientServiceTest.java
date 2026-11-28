package com.resourceallocator.service;
// AI-generated (Claude Code, Claude Opus 5.5)

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.resourceallocator.auth.ApiKeyHasher;
import com.resourceallocator.dto.CreateClientRequest;
import com.resourceallocator.dto.ClientResponse;
import com.resourceallocator.dto.CreateClientResponse;
import com.resourceallocator.dto.UpdateClientLimitsRequest;
import com.resourceallocator.exception.ConflictException;
import com.resourceallocator.exception.InvalidRequestException;
import com.resourceallocator.model.JobStatus;
import com.resourceallocator.model.Organization;
import com.resourceallocator.model.ResourceType;
import com.resourceallocator.repository.JobRepository;
import com.resourceallocator.repository.OrganizationRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobDispatcher jobDispatcher;

    private ClientService clientService;

    @BeforeEach
    void setUp() {
        clientService = new ClientService(organizationRepository,
                jobRepository, jobDispatcher);
    }

    @Test
    void createClientStoresOnlyTheKeyHash() {
        when(organizationRepository.saveAndFlush(any(Organization.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0),
                        3L));

        CreateClientResponse response = clientService.createClient(
                new CreateClientRequest("acme", 8, 2));

        ArgumentCaptor<Organization> saved =
                ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).saveAndFlush(saved.capture());
        assertThat(response.apiKey()).isNotBlank();
        assertThat(saved.getValue().getApiKeyHash())
                .isEqualTo(ApiKeyHasher.hash(response.apiKey()))
                .isNotEqualTo(response.apiKey());
        assertThat(response.id()).isEqualTo(3L);
        assertThat(response.orgName()).isEqualTo("acme");
        assertThat(response.maxCpus()).isEqualTo(8);
        assertThat(response.maxGpus()).isEqualTo(2);
        assertThat(response.createdAt()).isNotNull();
    }

    @Test
    void createClientIssuesDifferentKeysEachTime() {
        when(organizationRepository.saveAndFlush(any(Organization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String first = clientService.createClient(
                new CreateClientRequest("a", 1, 1)).apiKey();
        String second = clientService.createClient(
                new CreateClientRequest("b", 1, 1)).apiKey();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void createClientTrimsName() {
        when(organizationRepository.saveAndFlush(any(Organization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateClientResponse response = clientService.createClient(
                new CreateClientRequest("  acme  ", 1, 1));

        verify(organizationRepository).existsByOrgName("acme");
        assertThat(response.orgName()).isEqualTo("acme");
    }

    @Test
    void createClientRejectsTakenName() {
        when(organizationRepository.existsByOrgName("acme")).thenReturn(true);
        CreateClientRequest request = new CreateClientRequest("acme", 1, 1);

        assertThatThrownBy(() -> clientService.createClient(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("acme");
        verify(organizationRepository, never()).saveAndFlush(any());
    }

    @Test
    void createClientTurnsUniqueViolationIntoConflict() {
        when(organizationRepository.saveAndFlush(any(Organization.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        CreateClientRequest request = new CreateClientRequest("acme", 1, 1);

        assertThatThrownBy(() -> clientService.createClient(request))
                .isInstanceOf(ConflictException.class)
                // AI-edited (Claude Code, Claude Opus 5.5): the database
                // error is kept as the cause (PMD PreserveStackTrace fix)
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }

    // BEGIN AI-generated (Claude Code, Claude Opus 5.5)
    @Test
    void updateLimitsChangesGivenLimitAndKeepsTheOther() {
        Organization locked = lockedOrganization(8, 2);

        ClientResponse response = clientService.updateLimits(
                detachedCopy(), new UpdateClientLimitsRequest(16, null));

        assertThat(response.maxCpus()).isEqualTo(16);
        assertThat(response.maxGpus()).isEqualTo(2);
        assertThat(locked.getMaxCpus()).isEqualTo(16);
        assertThat(locked.getMaxGpus()).isEqualTo(2);
    }

    @Test
    void updateLimitsReadsCurrentValuesFromTheLockedRow() {
        // The locked row has GPU limit 5, though the copy from
        // authentication still says 2; the omitted GPU limit keeps 5.
        lockedOrganization(8, 5);

        ClientResponse response = clientService.updateLimits(
                detachedCopy(), new UpdateClientLimitsRequest(1, null));

        assertThat(response.maxGpus()).isEqualTo(5);
    }

    @Test
    void updateLimitsLocksThenSavesThenDispatches() {
        lockedOrganization(8, 2);

        clientService.updateLimits(detachedCopy(),
                new UpdateClientLimitsRequest(4, 4));

        InOrder order = inOrder(organizationRepository, jobDispatcher);
        order.verify(organizationRepository).findByIdForUpdate(3L);
        order.verify(organizationRepository).flush();
        order.verify(jobDispatcher).dispatch(3L);
    }

    @Test
    void updateLimitsRequiresAtLeastOneLimit() {
        UpdateClientLimitsRequest request =
                new UpdateClientLimitsRequest(null, null);

        assertThatThrownBy(() ->
                clientService.updateLimits(detachedCopy(), request))
                .isInstanceOf(InvalidRequestException.class);
        verify(organizationRepository, never()).findByIdForUpdate(any());
        verify(jobDispatcher, never()).dispatch(anyLong());
    }

    @Test
    void updateLimitsRejectsLimitBelowAQueuedJob() {
        Organization locked = lockedOrganization(8, 2);
        when(jobRepository.existsWithMoreUnitsThan(eq(3L),
                eq(JobStatus.QUEUED), any(ResourceType.class), anyInt()))
                .thenAnswer(invocation ->
                        invocation.getArgument(2) == ResourceType.GPU
                                && (int) invocation.getArgument(3) == 1);
        UpdateClientLimitsRequest request =
                new UpdateClientLimitsRequest(null, 1);

        assertThatThrownBy(() ->
                clientService.updateLimits(detachedCopy(), request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("GPU");
        assertThat(locked.getMaxGpus()).isEqualTo(2);
        verify(jobDispatcher, never()).dispatch(anyLong());
    }

    @Test
    void updateLimitsChecksQueuedJobsAgainstTheNewLimits() {
        lockedOrganization(8, 2);

        clientService.updateLimits(detachedCopy(),
                new UpdateClientLimitsRequest(3, null));

        verify(jobRepository).existsWithMoreUnitsThan(
                eq(3L), eq(JobStatus.QUEUED), eq(ResourceType.CPU), eq(3));
        verify(jobRepository).existsWithMoreUnitsThan(
                eq(3L), eq(JobStatus.QUEUED), eq(ResourceType.GPU), anyInt());
    }

    // The row that findByIdForUpdate returns, with the given limits.
    private Organization lockedOrganization(final int maxCpus,
            final int maxGpus) {
        Organization locked = withId(
                new Organization("acme", "hash", maxCpus, maxGpus), 3L);
        when(organizationRepository.findByIdForUpdate(3L))
                .thenReturn(Optional.of(locked));
        return locked;
    }

    // The copy loaded during authentication, possibly out of date.
    private static Organization detachedCopy() {
        return withId(new Organization("acme", "hash", 8, 2), 3L);
    }
    // END AI-generated

    private static Organization withId(final Organization organization,
            final long id) {
        ReflectionTestUtils.setField(organization, "id", id);
        return organization;
    }
}
